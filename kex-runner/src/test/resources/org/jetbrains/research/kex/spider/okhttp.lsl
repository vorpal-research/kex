library okhttp2;

types {
    Request$Builder (com.squareup.okhttp.Request$Builder);
    Request (com.squareup.okhttp.Request);
    OkHttpClient (com.squareup.okhttp.OkHttpClient);
    Call (com.squareup.okhttp.Call);
    Response (com.squareup.okhttp.Response);
    ResponseBody (com.squareup.okhttp.ResponseBody);
    String (String);
}

automaton Request$Builder {
    javapackage com.squareup.okhttp;
    state Created, URLSet;

    shift Created->URLSet(url);
    shift URLSet->self(build);
}

fun Request$Builder.Request$Builder(): Request$Builder {
    result = new Request$Builder(Created);
}

fun Request$Builder.url(url: String): Request$Builder;

fun Request$Builder.build(): Request {
    result = new Request(Created);
}

automaton Request {
    javapackage com.squareup.okhttp;
    state Created;
}

fun Request.Request(): Request {
    result = new Request(Created);
}

automaton OkHttpClient {
    javapackage com.squareup.okhttp;

    state Created;
}

fun OkHttpClient.OkHttpClient(): OkHttpClient {
    result = new OkHttpClient(Created);
}

fun OkHttpClient.newCall(request: Request): Call {
    result = new Call(Created);
}

automaton Call {
    javapackage com.squareup.okhttp;

    state Created;
}

fun Call.Call(): Call {
    result = new Call(Created);
}

fun Call.execute(request: Request): Response {
    result = new Response(Created);
}

automaton Response {
    javapackage com.squareup.okhttp;

    state Created;
}

fun Response.Response(): Response {
    result = new Response(Created);
}

fun Response.body(): ResponseBody {
    result = new ResponseBody(Created);
}

automaton ResponseBody {
    javapackage com.squareup.okhttp;

    state Created;
}

fun ResponseBody.ResponseBody(): ResponseBody {
    result = new ResponseBody(Created);
}

fun ResponseBody.string(): String;