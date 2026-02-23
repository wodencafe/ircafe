package cafe.woden.ircclient.app.interceptors;

/** Match mode for interceptor rule text matching. */
public enum InterceptorRuleMode {
  ALL,
  NONE,
  LIKE,
  GLOB,
  REGEX
}
