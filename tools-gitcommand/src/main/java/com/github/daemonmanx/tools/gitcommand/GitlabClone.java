package com.github.daemonmanx.tools.gitcommand;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;

import lombok.SneakyThrows;

/**
 * group组项目检出
 *
 * <p> group下项目众多，需要调用多次{@code git clone}才能下载完成，
 * 该代码可以一次性将group组项目检出。
 *
 * <p> 使用前需要安装git命令{@link https://git-scm.com/downloads}并生成密钥{@link https://docs.gitlab.com/ee/ssh/}，
 * 将公钥维护到gitlab{@value keys --> SSH Keys}。
 * 查看access token{@value profile --> Access Tokens}和group id{@value Group ID: ?}，作为参数传递到脚本中。
 *
 * <p> 注意：新域名首次{@code git clone}时会进入交互模式，脚本未处理此情况。
 * 可以手动调用{@code git clone}，进入交互模式，选{@code y}将地址加入到信任列表，然后执行脚本。
 *
 * @see Runtime#exec(String, String[], File)
 * @author daemonmanx
 * @since 2021-09-14
 */
public class GitlabClone {
    //path
    private static final String ROOT_PATH = "D:/Code/backup-gitlab";
    //group id
    private static final int groupId = 34;
    //Access Tokens
    private static final String token = "--";

    public static void main(String[] args) {
        //cloneByGroup(groupId);
        cloneAllCanAccess();
    }

    public static void cloneAllCanAccess() {
        supplierPerPages("http://gitlab.cbi.com/api/v4/groups", (array) -> {
            GitlabClone clone = new GitlabClone();
            for (int i = 0; i < array.size(); i++) {
                int groupId = array.getJSONObject(i).getIntValue("id");
                clone.projects(groupId);
                clone.subGroups(groupId);
            }
        });
    }

    public static void cloneByGroup(int groupId) {
        GitlabClone clone = new GitlabClone();
        clone.projects(groupId);
        clone.subGroups(groupId);
    }


    @SneakyThrows
    private void projects(int groupId) {
        supplierPerPages("http://gitlab.cbi.com/api/v4/groups/" + groupId + "/projects", (array) -> {
            Runtime runtime = Runtime.getRuntime();
            for (int i = 0; i < array.size(); i++) {
                String full_path = array.getJSONObject(i).getJSONObject("namespace").getString("full_path");
                if(full_path.contains("hengshan") || full_path.contains("lushan")) {
                    continue;
                }
                //工作目录，命令输入/输出相对路径
                File workDir = new File(ROOT_PATH + File.separator + full_path);
                if(!workDir.exists()) {
                    workDir.mkdirs();
                }
                String url = array.getJSONObject(i).getString("ssh_url_to_repo");
                String command = String.format("git clone %s", url);
                System.out.println(command);

                Process process = null;
                try {
                    process = runtime.exec(command, null, workDir);
                    stdout(process.getInputStream(), process.getErrorStream());
                    int exit = process.waitFor();
                } catch(Exception e) {
                    e.printStackTrace();
                } finally {
                    if(process != null) {
                        process.destroy();
                    }
                }
            }
        });
    }

    @SneakyThrows
    private void subGroups(int groupId) {
        supplierPerPages("http://gitlab.cbi.com/api/v4/groups/" + groupId + "/subgroups", (subGroups) -> {
            for (int i = 0; i < subGroups.size(); i++) {
                int id = subGroups.getJSONObject(i).getIntValue("id");
                projects(id);
                subGroups(id);
            }
        });
    }

    private static void supplierPerPages(String url, Consumer<JSONArray> consumer) {
        for(int page = 1; ; page++) {
            String content = OkHttpCli.url( String.format(url + "?private_token=%s&per_page=50&page=%s", token, page))
                                  .get(new HashMap<>())
                                  .getContent();
            JSONArray array = JSON.parseArray(content);
            if(array.isEmpty()) {
                break;
            }
            consumer.accept(array);
        }
    }

    private void stdout(InputStream... inputs){
        ExecutorService executorService = Executors.newFixedThreadPool(inputs.length);
        for (InputStream in : inputs) {
            executorService.execute(() -> {
                try{
                    BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line = null;
                    while((line = br.readLine())!=null){
                        System.out.println(line);
                    }
                }catch (IOException e){
                    e.printStackTrace();
                }finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        executorService.shutdown();
    }
}
