package mvcframework.servlet;

import mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pp on 2020/3/12.
 */
public class DispatcherServlet extends HttpServlet {

    //保存applicaiton.properties配置项内容
    private Properties contextConfig = new Properties();

    //保存类名
    private List<String> classNames = new ArrayList<>();

    //保存类的实例
    private Map<String, Object> ioc = new ConcurrentHashMap<>();

    //保存url和method的对应关系
    private List<Handler> handlerMapping = new ArrayList<>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispacher(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Service Exception" + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispacher(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Handler handler = getHandler(req);
        if (null == handler) {
            resp.getWriter().write("404 Not Found !!");
            return;
        }
        Class<?>[] paramsType = handler.method.getParameterTypes();
        Object[] paramsValue = new Object[paramsType.length];
        Map<String, String[]> params = req.getParameterMap();
        for (Map.Entry<String, String[]> param : params.entrySet()) {
            String value = Arrays.toString(param.getValue())
                    .replaceAll("\\[|\\]", "")
                    .replaceAll("\\s", ",");
            if (!handler.paramIndexMapping.containsKey(param.getKey())) {
                continue;
            }
            int index = handler.paramIndexMapping.get(param.getKey());
            paramsValue[index] = convert(paramsType[index], value);
        }
        if (handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())) {
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramsValue[reqIndex] = req;
        }
        if (handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramsValue[respIndex] = resp;
        }
        Object returnValue = handler.method.invoke(handler.controller, paramsValue);
        if (null == returnValue || returnValue instanceof Void) {
            return;
        }
        resp.getWriter().write(returnValue.toString());
    }

    private Handler getHandler(HttpServletRequest req) {
        if (handlerMapping.isEmpty()) {
            return null;
        }
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");
        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            if (!matcher.matches()) {
                continue;
            }
            return handler;
        }
        return null;
    }

    private Object convert(Class<?> type, String value) {
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        return value;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2.扫描相关的类
        doScan(contextConfig.getProperty("scanPackage"));

        //3.初始化扫描的类
        doInstance();

        //4.完成依赖注入
        doAutowired();

        //5.初始化handlerMapping
        initHandlerMapping();

        System.out.println("GP Spring framework is init ...");
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(GPController.class)) {
                continue;
            }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
                GPRequestMapping gpRequestMapping = clazz.getAnnotation(GPRequestMapping.class);
                baseUrl = gpRequestMapping.value();
            }
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(GPRequestMapping.class)) {
                    continue;
                }
                GPRequestMapping mapping = method.getAnnotation(GPRequestMapping.class);
                String regex = ("/" + baseUrl + mapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(entry.getValue(), method, pattern));
                System.out.println("mapping" + regex + "," + method);
            }
        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(GPAutowired.class)) {
                    continue;
                }
                GPAutowired gpAutowired = field.getAnnotation(GPAutowired.class);
                String beanName = gpAutowired.value();
                if ("".equals(beanName.trim())) {
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(GPController.class)) {
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(GPService.class)) {
                    GPService gpService = clazz.getAnnotation(GPService.class);
                    String beanName = gpService.value();
                    if ("".equals(beanName.trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception(i.getName() + " is exist!!");
                        }
                        ioc.put(i.getName(), instance);

                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doScan(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replace(".", "/"));
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                doScan(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith("class")) {
                    continue;
                } else {
                    String fileName = scanPackage + "." + file.getName().replace(".class", "");
                    classNames.add(fileName);
                }
            }
        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream in = null;
        try {
            in = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
            contextConfig.load(in);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != in) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private class Handler {

        protected Object controller;

        protected Method method;

        protected Pattern pattern;

        protected Map<String, Integer> paramIndexMapping;

        protected Handler(Object controller, Method method, Pattern pattern) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndexMapping = new HashMap<>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            Annotation[][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                for (Annotation a : pa[i]) {
                    String paramName = ((GPRequestParam) a).value();
                    if (!"".equals(paramName.trim())) {
                        paramIndexMapping.put(paramName, i);
                    }
                }
            }
            Class<?>[] paramsType = method.getParameterTypes();
            for (int i = 0; i < paramsType.length; i++) {
                Class<?> type = paramsType[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }
        }
    }
}
