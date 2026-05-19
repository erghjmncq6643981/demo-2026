package com.chandler.instance.client.example.service;

import com.chandler.instance.client.example.domain.dataobject.User;
import com.chandler.instance.client.example.domain.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 *
 * @author 钱丁君-chandler 2025/12/5
 */
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;

    public User detail(Long id ) {
        return userMapper.selectById(id);
    }

    public void add(User user) {
        userMapper.insert(user);
    }

    public void update(User user) {
        userMapper.updateById(user);
    }

    public void delete(Long id) {
        userMapper.deleteById(id);
    }
}
