package bupt.evchargebackend.entity.user;

import bupt.evchargebackend.entity.user.enums.UserRole;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号表。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Data
@TableName("user_account")
public class UserAccount {

    /** 主键 */
    @TableId
    private String userId;

    /** 用户名，唯一 */
    private String username;

    /** 加密后的密码 */
    private String passwordHash;

    /** 用户角色 */
    private UserRole role;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
