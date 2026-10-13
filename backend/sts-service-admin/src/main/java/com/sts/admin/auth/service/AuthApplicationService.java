package com.sts.admin.auth.service;

import com.sts.admin.auth.dto.LoginRequest;
import com.sts.admin.auth.dto.LoginVO;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthApplicationService {

    LoginVO login(LoginRequest request, HttpServletRequest servletRequest);
}
