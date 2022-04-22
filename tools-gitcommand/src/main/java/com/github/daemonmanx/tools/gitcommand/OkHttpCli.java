package com.github.daemonmanx.tools.gitcommand;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSONObject;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by Lxa on 2018/3/17.
 *
 * @author daemonmanx
 */
@Slf4j
public class OkHttpCli {

  private static final long CONN_TIMEOUT = 10L;

  private static final long READ_TIMEOUT = 15L;

  private static final long WRITE_TIMEOUT = 15L;

  private static final OkHttpClient client = new OkHttpClient.Builder()
      .connectTimeout(CONN_TIMEOUT, TimeUnit.SECONDS)
      .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
      .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build();

  public static Req url(String urlStr) {
    OkHttpCli cli = new OkHttpCli();
    URL url;
    try {
      url = new URL(urlStr);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("解析URL失败:" + urlStr);
    }
    return cli.req(url);
  }

  private Req req(URL url) {
    return new Req(url);
  }

  public class Req {

    private final Request.Builder builder;
    private final URL url;
    private int statusCode;
    private String statusMessage;
    private String content;

    private Req(URL url) {
      this.url = url;
      this.builder = new Request.Builder();
    }

    /**
     * 增加请求头，可多次调用
     *
     * @param headerKey 请求头key
     * @param headerVal 请求头value
     * @return 返回this
     */
    public Req addHeader(String headerKey, String headerVal) {
      builder.addHeader(headerKey, headerVal);
      return this;
    }

    public Req get(Map<String, String> get) {
      if (get == null || get.size() == 0) {
        builder.url(url).get();
      } else {
        builder.url(url + "?" + Maps.toUrl(get)).get();
      }
      return send();
    }

    public Req post(Map<String, String> post, Media media) {
      builder.url(url).post(RequestBody.create(media.getMediaType(), media.getContent(post)));
      return send();
    }

    // public Req post(Map<String, String> post,String encoding) {
    // String requestParamString = getRequestParamString(post, encoding);
    // RequestBody body =
    // RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"),requestParamString);
    // builder.url(url).post(body);
    // return send();
    // }

    public Req post(JSONObject json, Media media) {
      builder.url(url).post(RequestBody.create(media.getMediaType(), json.toJSONString()));
      return send();
    }

    public Req put(Map<String, String> post, Media media) {
      builder.url(url).put(RequestBody.create(media.getMediaType(), media.getContent(post)));
      return send();
    }

    public Req delete(Map<String, String> post, Media media) {
      builder.url(url).delete(RequestBody.create(media.getMediaType(), media.getContent(post)));
      return send();
    }

    private Req send() {
      Response response = null;
      try {
        Request request = builder.build();
        response = client.newCall(request).execute();
        content = response.body() == null ? "" : response.body().string();
        this.statusCode = response.code();
        this.statusMessage = response.message();
        return this;
      } catch (ConnectException e) {
        throw new RuntimeException(e);
      } catch (SocketTimeoutException e) {
        if ("connect timed out".equals(e.getMessage())) {
          throw new RuntimeException(e);
        } else if ("Read timed out".equals(e.getMessage())) {
          throw new RuntimeException(e);
        }
        throw new RuntimeException("网络超时:" + url, e);
      } catch (IOException e) {
        throw new RuntimeException("发送数据异常:" + url, e);
      } finally {
        if (response != null) {
          try {
            response.close();
          } catch (Exception e) {
            response = null;
          }
        }
      }
    }

    public int getStatusCode() {
      return statusCode;
    }

    public String getStatusMessage() {
      return statusMessage;
    }

    public String getContent() {
      return content;
    }

  }

  private interface Package {

    String pack(Map<String, String> content);

  }

  public enum Media {

    JSON_UTF8(MediaType.parse("application/json; charset=utf-8"), JSONObject::toJSONString),
    URL_UTF8(MediaType.parse("application/x-www-form-urlencoded; charset=utf-8"), Maps::toUrl),
    STR_UTF8(MediaType.parse("application/x-www-form-urlencoded; charset=utf-8"), Maps::toEncoderUrlUtf8),
    URL_GBK(MediaType.parse("application/x-www-form-urlencoded; charset=gbk"), Maps::toUrl),
    URL_GB2312(MediaType.parse("application/x-www-form-urlencoded; charset=gb2312"), Maps::toUrl);

    private final MediaType mediaType;
    private final Package pack;

    Media(MediaType mediaType, Package pack) {
      this.mediaType = mediaType;
      this.pack = pack;
    }

    public MediaType getMediaType() {
      return mediaType;
    }

    public String getContent(Map<String, String> map) {
      return pack.pack(map);
    }
  }

  public static class Maps {

    public static Map<String, String> sort(Map<String, String> map) {
      Map<String, String> sortMap = new LinkedHashMap<>();
      map.entrySet().stream().sorted(Entry.comparingByKey()).forEachOrdered(e -> sortMap.put(e.getKey(), e.getValue()));
      return sortMap;
    }

    public static void encoder(Map<String, String> map, Charset charset) {
      for (Entry<String, String> entry : map.entrySet()) {
        try {
          entry.setValue(URLEncoder.encode(entry.getValue(), charset.name()));
        } catch (UnsupportedEncodingException ignored) {

        }
      }
    }

    public static String toUrl(Map<String, String> map) {
      return map.entrySet().stream().map(v -> v.getKey() + "=" + v.getValue()).collect(Collectors.joining("&"));
    }

    public static String toEncoderUrlUtf8(Map<String, String> requestParam) {
      StringJoiner joiner = new StringJoiner("&");
      for (Entry<String, String> v : requestParam.entrySet()) {
        String s = null;
        try {
          s = v.getKey() + "=" + URLEncoder.encode(v.getValue(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
          log.error("getRequestParamString UnsupportedEncodingException:", e);
          return "";
        }
        joiner.add(s);
      }
      return joiner.toString();
    }
  }
}
