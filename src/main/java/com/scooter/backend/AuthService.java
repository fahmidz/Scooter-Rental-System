package com.scooter.backend;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import static com.scooter.backend.ApiError.Failure;

@Service
public class AuthService {
  private final JdbcTemplate db;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();
  public AuthService(JdbcTemplate db) { this.db = db; }
  public record Account(int id, String role) {}
  private String hash(String token) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
  public Map<String,Object> register(String name, String email, String password) {
    if (name == null || name.isBlank() || name.length()>100 || email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || email.length()>150 || password == null || password.length()<8 || password.length()>200)
      throw new Failure(HttpStatus.BAD_REQUEST,"Name, valid email and password (8+ characters) required");
    Integer existing = db.queryForObject("SELECT count(*) FROM users WHERE lower(email)=lower(?)",Integer.class,email);
    if (existing != null && existing>0) throw new Failure(HttpStatus.CONFLICT,"Email already registered");
    Integer id=db.queryForObject("INSERT INTO users (name,email,password_hash,role) VALUES (?,?,?,'user') RETURNING user_id",Integer.class,name.trim(),email.trim().toLowerCase(),encoder.encode(password));
    return Map.of("userId",id,"name",name.trim(),"email",email.trim().toLowerCase());
  }
  public Map<String,Object> login(String email, String password) {
    var users=db.query("SELECT user_id,password_hash,role FROM users WHERE lower(email)=lower(?)",(rs,n)->Map.of("id",rs.getInt("user_id"),"hash",rs.getString("password_hash"),"role",rs.getString("role")),email==null?"":email);
    if(users.isEmpty() || password==null || !encoder.matches(password,(String)users.get(0).get("hash"))) throw new Failure(HttpStatus.UNAUTHORIZED,"Invalid credentials");
    byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    int id=(int)users.get(0).get("id");
    db.update("INSERT INTO auth_tokens (user_id,token_hash,expires_at) VALUES (?,?,?)",id,hash(token),LocalDateTime.now().plusDays(7));
    return Map.of("accessToken",token,"tokenType","Bearer","expiresInSeconds",604800,"userId",id,"role",users.get(0).get("role"));
  }
  public Account require(String header) {
    if(header==null || !header.startsWith("Bearer ")) throw new Failure(HttpStatus.UNAUTHORIZED,"Bearer token required");
    var rows=db.query("SELECT u.user_id,u.role FROM auth_tokens t JOIN users u ON u.user_id=t.user_id WHERE t.token_hash=? AND t.expires_at>now()",(rs,n)->new Account(rs.getInt(1),rs.getString(2)),hash(header.substring(7).trim()));
    if(rows.isEmpty()) throw new Failure(HttpStatus.UNAUTHORIZED,"Invalid or expired token");
    return rows.get(0);
  }
  public Account admin(String header) {
    Account a=require(header);
    if(!"admin".equals(a.role())) throw new Failure(HttpStatus.FORBIDDEN,"Admin role required");
    return a;
  }
  public Map<String,Object> profile(String header) {
    Account a=require(header);
    return db.queryForMap("SELECT user_id,name,email,role FROM users WHERE user_id=?",a.id());
  }
  public void logout(String header) {
    require(header);db.update("DELETE FROM auth_tokens WHERE token_hash=?",hash(header.substring(7).trim()));
  }
}
