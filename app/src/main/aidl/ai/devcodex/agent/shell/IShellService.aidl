// Shizuku UserService：以 shell(ADB) 权限执行
package ai.devcodex.agent.shell;

interface IShellService {
    String exec(String command);          // 执行 shell 命令，返回 stdout+stderr
    String read(String path);             // 读文本文件
    void write(String path, String data); // 写文本文件
    String list(String path);             // 列目录
    boolean delete(String path);          // 删除文件/目录
}
