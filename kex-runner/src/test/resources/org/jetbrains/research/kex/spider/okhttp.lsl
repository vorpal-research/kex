libsl "1.0.0";
library okhttp version "2.7.5" url "https://github.com/square/okhttp";

types {
    Request$Builder (com.squareup.okhttp.Request$Builder);
    Request (com.squareup.okhttp.Request);
    OkHttpClient (com.squareup.okhttp.OkHttpClient);
    Call (com.squareup.okhttp.Call);
    Response (com.squareup.okhttp.Response);
    ResponseBody (com.squareup.okhttp.ResponseBody);
    String (string);
}

automaton com.squareup.okhttp.Request$Builder : Request$Builder {
    initstate Created;
    state URLSet;

    shift Created->URLSet(`url`);
    shift URLSet->self(build);

    fun `url`(`url`: String): Request$Builder;

    fun build(): Request;
}
