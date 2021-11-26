libsl "1.0.0";
library okhttp version "2.7.5" url "https://github.com/square/okhttp";

types {
    Request$Builder (okhttp3.Request$Builder);
    Request (okhttp3.Request);
    OkHttpClient (okhttp3.OkHttpClient);
    Call (okhttp3.OkHttpClient.Call);
    Response (okhttp3.Call.Response);
    ResponseBody (okhttp3.Response.ResponseBody);
    RequestBody (okhttp3.RequestBody);
    String (string);
    Long (int64);
    TimeUnit (java.util.concurrent.TimeUnit);
}

automaton okhttp3.Request$Builder : Request$Builder {
    initstate Created;
    state URLSet;

    shift Created->URLSet(`url`);
    shift URLSet->self(build);

    fun `url`(`url`: String): Request$Builder
        requires urlIsNotEmpty: `url` != "";

    fun connectTimeout(timeout: Long, unit: TimeUnit)
        requires timeoutIsPositive: timeout > 0;

    fun callTimeout(timeout: Long, unit: TimeUnit)
        requires timeoutIsPositive: timeout > 0;

    fun readTimeout(timeout: Long, unit: TimeUnit)
        requires timeoutIsPositive: timeout > 0;

    fun method(method: String, body: RequestBody)
        requires isMethodKnown: method = "GET" | method = "POST" | method = "HEAD" | method = "PUT" | method = "DELETE" | method = "PATCH";

    fun build(): Request;
}
