libsl "1.0.0";
library okhttp version "2.7.5" url "https://github.com/square/okhttp";

types {
    Request$Builder (okhttp3.Request$Builder);
    Request (okhttp3.Request);
    OkHttpClient (okhttp3.OkHttpClient);
    Call (okhttp3.OkHttpClient.Call);
    Response (okhttp3.Call.Response);
    ResponseBody (okhttp3.Response.ResponseBody);
    String (string);
}

automaton okhttp3.Request$Builder : Request$Builder {
    initstate Created;
    state URLSet;

    shift Created->URLSet(`url`);
    shift URLSet->self(build);

    fun `url`(`url`: String): Request$Builder;

    fun build(): Request;
}
