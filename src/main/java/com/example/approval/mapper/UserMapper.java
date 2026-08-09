package com.example.approval.mapper;

import com.example.approval.entity.SystemUser;
import com.example.approval.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for User entity.
 * Provides methods used by ApprovalService for dynamic assignee resolution.
 */
@Mapper
public interface UserMapper {


    SystemUser findByUsername(@Param("username") String username);

}
