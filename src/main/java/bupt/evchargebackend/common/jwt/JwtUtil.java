package bupt.evchargebackend.common.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌工具。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Component
public class JwtUtil {

    private final SecretKey key;

    /**
     * 从配置读取密钥并构造 HMAC-SHA 签名密钥。
     */
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT 令牌，有效期 24 小时。
     *
     * @param userId 用户 ID（存入 subject）
     * @param role   用户角色（存入 claim "role"）
     * @return 签名后的 JWT 字符串
     */
    public String generate(String userId, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + 86400000))
                .signWith(key)
                .compact();
    }

    /**
     * 解析令牌中的用户 ID（subject）。
     *
     * @param token JWT 字符串
     * @return 用户 ID
     */
    public String parseUserId(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * 解析令牌中的用户角色。
     *
     * @param token JWT 字符串
     * @return 角色名（USER / ADMIN）
     */
    public String parseRole(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }
}
