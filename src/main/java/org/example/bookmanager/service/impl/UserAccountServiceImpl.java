package org.example.bookmanager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.bookmanager.common.BizException;
import org.example.bookmanager.dto.LoginRequest;
import org.example.bookmanager.dto.RegisterRequest;
import org.example.bookmanager.dto.UserDTO;
import org.example.bookmanager.entity.UserAccount;
import org.example.bookmanager.mapper.UserAccountMapper;
import org.example.bookmanager.service.UserAccountService;
import org.example.bookmanager.util.PasswordUtil;
import org.springframework.stereotype.Service;

@Service
public class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountMapper userAccountMapper;

    public UserAccountServiceImpl(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public UserDTO register(RegisterRequest request) {
        QueryWrapper<UserAccount> wrapper = new QueryWrapper<>();
        wrapper.eq("username", request.getUsername());
        Long count = userAccountMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(409, "username already exists");
        }

        UserAccount user = new UserAccount();
        user.setUsername(request.getUsername());
        user.setPassword(PasswordUtil.sha256Hex(request.getPassword()));
        userAccountMapper.insert(user);
        return new UserDTO(user.getId(), user.getUsername());
    }

    @Override
    public UserDTO login(LoginRequest request) {
        QueryWrapper<UserAccount> wrapper = new QueryWrapper<>();
        wrapper.eq("username", request.getUsername());
        UserAccount user = userAccountMapper.selectOne(wrapper);
        if (user == null) {
            throw new BizException(401, "invalid username or password");
        }
        String hash = PasswordUtil.sha256Hex(request.getPassword());
        if (!hash.equals(user.getPassword())) {
            throw new BizException(401, "invalid username or password");
        }
        return new UserDTO(user.getId(), user.getUsername());
    }
}
