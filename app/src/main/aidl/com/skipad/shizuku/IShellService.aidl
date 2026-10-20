package com.skipad.shizuku;

interface IShellService {
    String exec(String command);
    void destroy();
    void exit();
}
