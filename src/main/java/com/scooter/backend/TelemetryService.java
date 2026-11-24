package com.scooter.backend;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class TelemetryService {
 private final JdbcTemplate db;
 public TelemetryService(JdbcTemplate db){this.db=db;}
 private static double radians(double d){return Math.toRadians(d);}
 static double haversineKm(double a,double b,double c,double d){double lat=radians(c-a),lon=radians(d-b);double s=Math.pow(Math.sin(lat/2),2)+Math.cos(radians(a))*Math.cos(radians(c))*Math.pow(Math.sin(lon/2),2);return 6371.0*2*Math.asin(Math.min(1,Math.sqrt(s)));}
 @Transactional public void process(int topicId,JsonNode n){
   if(!n.has("scooterId")||n.get("scooterId").asInt()!=topicId||!n.has("qrCode")||!n.has("batteryLevel")||!n.has("latitude")||!n.has("longitude")||!n.has("status"))return;
   int battery=n.get("batteryLevel").asInt(-1);double lat=n.get("latitude").asDouble(Double.NaN),lon=n.get("longitude").asDouble(Double.NaN);
   if(battery<0||battery>100||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return;
   String qr=n.get("qrCode").asText();if(!qr.equals(String.format("SCT-%03d",topicId)))return;
   String status=n.get("status").asText();if(!List.of("available","in_use","maintenance").contains(status))return;
   // Serializes telemetry for this scooter with other telemetry updates and ride operations.
   List<Map<String,Object>> existing=db.queryForList("SELECT scooter_id,latitude,longitude,status FROM scooters WHERE scooter_id=? FOR UPDATE",topicId);
   if(existing.isEmpty()){
     db.update("INSERT INTO scooters(scooter_id,qr_code,battery_level,latitude,longitude,status) VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING",topicId,qr,battery,lat,lon,status);
     existing=db.queryForList("SELECT scooter_id,latitude,longitude,status FROM scooters WHERE scooter_id=? FOR UPDATE",topicId);
   }
   if(existing.isEmpty())return;
   Map<String,Object> old=existing.get(0);
   List<Map<String,Object>> rides=db.queryForList("SELECT ride_id FROM rides WHERE scooter_id=? AND status='active'",topicId);
   boolean active=!rides.isEmpty();
   Integer openIssues=db.queryForObject("SELECT count(*) FROM maintenance WHERE scooter_id=? AND status<>'resolved'",Integer.class,topicId);
   boolean requiresMaintenance=battery<15 || (openIssues!=null && openIssues>0);
   // A delayed device status must not make a reserved scooter available while it has an active ride.
   String effective=requiresMaintenance?"maintenance":(active?"in_use":("in_use".equals(status)?"available":status));
   db.update("UPDATE scooters SET battery_level=?,latitude=?,longitude=?,status=? WHERE scooter_id=?",battery,lat,lon,effective,topicId);
   double speed=n.path("speedKmh").asDouble(0);long timestamp=n.path("timestamp").asLong(0);
   db.update("INSERT INTO scooter_telemetry(scooter_id,battery_level,latitude,longitude,speed_kmh,device_timestamp) VALUES(?,?,?,?,?,?)",topicId,battery,lat,lon,Double.isFinite(speed)?speed:0,timestamp);
   if(active&&old.get("latitude")!=null&&old.get("longitude")!=null){
     double km=haversineKm(((Number)old.get("latitude")).doubleValue(),((Number)old.get("longitude")).doubleValue(),lat,lon);
     // Reject implausible jumps; coarse cap for 2-second simulator updates.
     if(km>0&&km<0.1)db.update("UPDATE rides SET distance_km=distance_km+? WHERE ride_id=?",km,rides.get(0).get("ride_id"));
   }
 }
}
