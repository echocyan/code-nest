package com.echocyan.codenest.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("`user`")
public class User extends AuditableEntity {

    private Long id;

    private String username;

    private String passwordHash;

    private String nickname;

    private String avatarUrl;

    private String bio;
}
