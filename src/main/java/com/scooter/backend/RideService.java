package com.scooter.backend;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.scooter.backend.ApiError.Failure;

@Service public class RideService {
 private final JdbcTemplate db;private final MqttGateway mqtt;
 public RideService(JdbcTemplate db,MqttGateway mqtt){this.db=db;this.mqtt=mqtt;}
 @Transactional public Map<String,Object> start(int userId,String qr){
  if(qr==null||qr.isBlank())throw new Failure(HttpStatus.BAD_REQUEST,"QR code required");
  var scooter=db.queryForList("SELECT scooter_id,status,battery_level FROM scooters WHERE qr_code=? FOR UPDATE",qr);
  if(scooter.isEmpty())throw new Failure(HttpStatus.NOT_FOUND,"Unknown scooter QR code");
  int scooterId=((Number)scooter.get(0).get("scooter_id")).intValue();
  if(!"available".equals(scooter.get(0).get("status"))||((Number)scooter.get(0).get("battery_level")).intValue()<15)throw new Failure(HttpStatus.CONFLICT,"Scooter unavailable");
  Integer active=db.queryForObject("SELECT count(*) FROM rides WHERE user_id=? AND status='active'",Integer.class,userId);
  if(active!=null&&active>0)throw new Failure(HttpStatus.CONFLICT,"User already has an active ride");
  // Publish before DB commit; lost acknowledgement/disconnect scenarios require operational reconciliation.
  mqtt.command(scooterId,"UNLOCK");
  Integer rideId=db.queryForObject("INSERT INTO rides(user_id,scooter_id,status) VALUES (?,?,'active') RETURNING ride_id",Integer.class,userId,scooterId);
  db.update("UPDATE scooters SET status='in_use' WHERE scooter_id=?",scooterId);
  return db.queryForMap("SELECT * FROM rides WHERE ride_id=?",rideId);
 }
 @Transactional public Map<String,Object> end(int userId,int rideId){
  // Find scooter id first; lock scooter before completing the ride.
  var ids=db.queryForList("SELECT scooter_id FROM rides WHERE ride_id=? AND user_id=? AND status='active'",rideId,userId);
  if(ids.isEmpty())throw new Failure(HttpStatus.NOT_FOUND,"Active ride not found");
  int scooterId=((Number)ids.get(0).get("scooter_id")).intValue();
  db.queryForList("SELECT scooter_id FROM scooters WHERE scooter_id=? FOR UPDATE",scooterId);
  var rides=db.queryForList("SELECT * FROM rides WHERE ride_id=? AND user_id=? AND status='active' FOR UPDATE",rideId,userId);
  if(rides.isEmpty())throw new Failure(HttpStatus.CONFLICT,"Ride already ended");
  // Demo pricing: $1 unlock + $0.25 per minute, minimum 1 minute.
  Long mins=db.queryForObject("SELECT GREATEST(1,CEIL(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-start_time))/60)::bigint) FROM rides WHERE ride_id=?",Long.class,rideId);
  BigDecimal cost=new BigDecimal("1.00").add(new BigDecimal("0.25").multiply(BigDecimal.valueOf(mins))).setScale(2,RoundingMode.HALF_UP);
  mqtt.command(scooterId,"LOCK");
  db.update("UPDATE rides SET end_time=now(),total_cost=?,status='completed' WHERE ride_id=?",cost,rideId);
  db.update("UPDATE scooters SET status=CASE WHEN battery_level<15 THEN 'maintenance' ELSE 'available' END WHERE scooter_id=?",scooterId);
  db.update("INSERT INTO payments(ride_id,amount,payment_method,payment_status) VALUES (?,?,'demo','pending') ON CONFLICT(ride_id) DO NOTHING",rideId,cost);
  return db.queryForMap("SELECT * FROM rides WHERE ride_id=?",rideId);
 }
 public Object history(int userId){return db.queryForList("SELECT * FROM rides WHERE user_id=? ORDER BY start_time DESC",userId);}
 public Object payment(int userId,int rideId){
   var rows=db.queryForList("SELECT p.* FROM payments p JOIN rides r ON r.ride_id=p.ride_id WHERE p.ride_id=? AND r.user_id=?",rideId,userId);
   if(rows.isEmpty())throw new Failure(HttpStatus.NOT_FOUND,"Payment not found");return rows.get(0);
 }
 @Transactional public Object simulatePayment(int userId,int rideId){
   var rows=db.queryForList("SELECT p.payment_id,p.payment_status FROM payments p JOIN rides r ON r.ride_id=p.ride_id WHERE p.ride_id=? AND r.user_id=? FOR UPDATE OF p",rideId,userId);
   if(rows.isEmpty())throw new Failure(HttpStatus.NOT_FOUND,"Payment not found");
   if("completed".equals(rows.get(0).get("payment_status")))return payment(userId,rideId);
   if(!"pending".equals(rows.get(0).get("payment_status")))throw new Failure(HttpStatus.CONFLICT,"Payment cannot be completed");
   db.update("UPDATE payments SET payment_status='completed',payment_date=now(),payment_method='demo' WHERE payment_id=?",rows.get(0).get("payment_id"));
   return payment(userId,rideId);
 }
}
