package com.hmdp.utils;

/**
 * @author zhaojiatao
 * @version 1.0.0
 * @date 2023/4/4 10:15
 * @Description
 * @ClassName ILock
 * Copyright: Copyright (c) 2022-2023 All Rights Reserved.
 */
public interface ILock {

    boolean tryLock(long timeoutSec);

    void unlock();
}
