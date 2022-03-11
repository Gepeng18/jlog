package com.jd.platform.jlog.common.handler;

import com.alibaba.fastjson.JSON;
import com.jd.platform.jlog.common.utils.CollectionUtil;
import com.jd.platform.jlog.common.utils.ConfigUtil;
import com.jd.platform.jlog.common.utils.StringUtil;
import com.sun.istack.internal.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jd.platform.jlog.common.constant.Constant.EXTRACT_MIN_LEN;
import static com.jd.platform.jlog.common.constant.Constant.TAG_NORMAL_KEY;
import static com.jd.platform.jlog.common.constant.Constant.TAG_NORMAL_KEY_MAX_LEN;
import static com.jd.platform.jlog.common.handler.CollectMode.*;
import static com.jd.platform.jlog.common.handler.CollectMode.E_LOG;
import static com.jd.platform.jlog.common.handler.CollectMode.E_REQ;
import static com.jd.platform.jlog.common.utils.ConfigUtil.RANDOM;

/**
 * @author tangbohu
 * @version 1.0.0
 * @ClassName Tag.java
 * @createTime 2022年02月12日 21:28:00
 */
public class TagHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TagHandler.class);

    private Set<String> reqTags;

    private Set<String> logTags;

    private Set<String> respTags;

    private String delimiter = "|";

    private int delimiterLen = delimiter.length();

    private String join = "=";

    private Pattern pattern;

    private long extract;

    private static volatile TagHandler instance = null;

    /**
     * 构建标签处理器
     * @param tagConfig 配置类
     */
    public static void buildTagHandler(TagConfig tagConfig) {

        if(tagConfig.getExtract() == SUSPEND){
            return;
        }

        TagHandler handler =  new TagHandler();
        handler.extract = tagConfig.getExtract();
        handler.reqTags = new HashSet<>(tagConfig.getReqTags());
        handler.logTags = new HashSet<>(tagConfig.getLogTags());
        handler.respTags = new HashSet<>(tagConfig.getRespTags());

        String regex = tagConfig.getRegex();
        if(StringUtil.isNotEmpty(regex)){
            handler.pattern = Pattern.compile(regex);
        }else{
            String escapeDelimiter = ConfigUtil.escapeExprSpecialWord(tagConfig.getDelimiter());
            String str = String.format("(%s[\\w\\W]*?%s)", escapeDelimiter, escapeDelimiter);
            handler.pattern = Pattern.compile(str);
        }
        handler.delimiter = tagConfig.getDelimiter();
        handler.delimiterLen = tagConfig.getDelimiter().length();
        handler.join = tagConfig.getJoin();
        instance = handler;
        LOGGER.info("构建标签处理器单例完成:{}",instance.toString());
    }


    /**
     * 提取请求参数里的标签
     * @param params 参数
     * @param ext 额外附加的,如ip等
     * @return tags
     */
    public static Map<String, Object> extractReqTag(Map<String, String[]> params, @NotNull Map<String, Object> ext) {

        if(instance == null || !isMatched(instance.extract, E_REQ)){ return null; }

        System.out.println("### INSTANCE.reqTags:"+JSON.toJSONString(instance.reqTags));

        Map<String, Object> requestMap = new HashMap<>(instance.reqTags.size());
        for (String tag : instance.reqTags) {
            Object val = ext.get(tag);
            if(val != null){
                requestMap.put(tag, val);
                continue;
            }

            if(CollectionUtil.isNotEmpty(params) && params.get(tag) != null){
                requestMap.put(tag, params.get(tag)[0]);
            }
        }
        System.out.println("提取到了请求入参日志标签："+JSON.toJSONString(requestMap));
        return requestMap;
    }


    /**
     * 提取普通log里标签
     * @param content 内容
     * @return tags
     */
    public static Map<String, Object> extractLogTag(String content) {
        if(instance == null || !isMatched(instance.extract, E_LOG) || content.length() < EXTRACT_MIN_LEN){
            return null;
        }

        Map<String,Object> tagMap = new HashMap<>(3);
        Matcher m = instance.pattern.matcher(content);
        while (m.find()) {
            String str = m.group().substring(instance.delimiterLen, m.group().length() - instance.delimiterLen);
            if(str.contains(instance.join)){
                String[] arr = str.split(instance.join);
                if(instance.logTags.contains(arr[0])){
                    tagMap.put(arr[0], arr[1]);
                }
            }else if(str.length() < TAG_NORMAL_KEY_MAX_LEN){
                tagMap.put(TAG_NORMAL_KEY, str);
            }else{
                if (RANDOM.nextInt(50) == 1) {
                    LOGGER.info("Some logs lack tags and are larger than 20 in length. Therefore, they are simply stored");
                }
            }
        }
        System.out.println("提取到了请求log日志标签："+JSON.toJSONString(tagMap));
        return tagMap;
    }


    /**
     * 提取返回结果的tag
     * @param resp 参数
     * @return map
     */
    public static Map<String, Object> extractRespTag(Map<String, Object> resp) {

        if(instance == null || !isMatched(instance.extract, E_REQ)){ return null; }

        System.out.println("### INSTANCE.reqTags:"+JSON.toJSONString(instance.reqTags));

        Map<String, Object> requestMap = new HashMap<>(instance.reqTags.size());
        for (String tag : instance.reqTags) {
            Object val = resp.get(tag);
            if(val != null){
                requestMap.put(tag, val);
            }
        }
        System.out.println("提取到了请求入参日志标签："+JSON.toJSONString(requestMap));
        return requestMap;
    }




    /**
     * 刷新标签处理器 加锁是为了防止极端情况下, 先到的config1覆盖后到的config2
     * @param tagConfig 新的配置
     */
    public synchronized static void refresh(TagConfig tagConfig) {
        instance = null;
        buildTagHandler(tagConfig);
    }


    @Override
    public String toString() {
        return "TagHandler{" +
                "reqTags=" + reqTags +
                ", logTags=" + logTags +
                ", delimiter='" + delimiter + '\'' +
                ", delimiterLen=" + delimiterLen +
                ", join='" + join + '\'' +
                ", pattern=" + pattern +
                ", extract=" + extract +
                '}';
    }

    // static Pattern BRACKET_PATTERN = Pattern.compile("(\\|[\\w\\W]*?\\|)");
    public static List<String> extractTest(String content) {

        List<String> list = new ArrayList<>();
        Matcher m = BRACKET_PATTERN.matcher(content);
        while (m.find()) {
            list.add(m.group().substring(1, m.group().length() - 1));
            //   list.add(m.group().substring(2, m.group().length() - 2));

        }
        return list;
    }


    //static Pattern BRACKET_PATTERN = Pattern.compile("(\\{\\{[\\w\\W]*?\\}\\})");
   // static Pattern BRACKET_PATTERN = Pattern.compile("(\\|\\|[\\w\\W]*?\\|\\|)");
    static Pattern BRACKET_PATTERN = Pattern.compile("(\\|[\\w\\W]*?\\|)");

    static String str1 = "||a=1||b=2||qwewe";
    static String str2 = "||a=1||b=2||qwewe||";
    static String str3 = "||a=1||eee||b=2";
    static String str4 = "||a=1||eee||b=2||";


    public static void main(String[] args) {
       System.out.println("msgByRegular1==> "+JSON.toJSONString(extractTest(str1)));
        System.out.println("msgByRegular2==> "+JSON.toJSONString(extractTest(str2)));
        System.out.println("msgByRegular3==> "+JSON.toJSONString(extractTest(str3)));
        System.out.println("msgByRegular4==> "+JSON.toJSONString(extractTest(str4)));
    }
}