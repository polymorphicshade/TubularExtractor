package org.schabi.newpipe.downloader;

import org.schabi.newpipe.downloader.ratelimiting.RateLimitedClientWrapper;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public final class DownloaderTestImpl extends Downloader {
    /**
     * Should be the latest Firefox ESR version.
     */
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";
    private static DownloaderTestImpl instance;
    private final RateLimitedClientWrapper clientWrapper;

    private DownloaderTestImpl(final OkHttpClient.Builder builder) {
        this.clientWrapper = new RateLimitedClientWrapper(builder
                .readTimeout(30, TimeUnit.SECONDS)
                // Required for certain services
                // For example Bandcamp otherwise fails on Windows with Java 17+
                // as their Fastly-CDN returns 403
                .connectionSpecs(Arrays.asList(ConnectionSpec.RESTRICTED_TLS))
                .build());
    }

    /**
     * It's recommended to call exactly once in the entire lifetime of the application.
     *
     * @param builder if null, default builder will be used
     * @return a new instance of {@link DownloaderTestImpl}
     */
    public static DownloaderTestImpl init(@Nullable final OkHttpClient.Builder builder) {
        instance = new DownloaderTestImpl(
                builder != null ? builder : new OkHttpClient.Builder());
        return instance;
    }

    public static DownloaderTestImpl getInstance() {
        if (instance == null) {
            init(null);
        }
        return instance;
    }

    @Override
    public Response execute(@Nonnull final Request request)
            throws IOException, ReCaptchaException {
        final String httpMethod = request.httpMethod();
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        RequestBody requestBody = null;
        if (dataToSend != null) {
            requestBody = RequestBody.create(dataToSend);
        }

        final okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT);

        headers.forEach((headerName, headerValueList) -> {
            requestBuilder.removeHeader(headerName);
            headerValueList.forEach(headerValue ->
                requestBuilder.addHeader(headerName, headerValue));
        });

        try (okhttp3.Response response =
                 clientWrapper.executeRequestWithLimit(requestBuilder.build())
        ) {
            if (response.code() == 429) {
                throw new ReCaptchaException("reCaptcha Challenge requested", url);
            }

            String responseBodyToReturn = null;
            try (ResponseBody body = response.body()) {
                if (body != null) {
                    responseBodyToReturn = body.string();
                }
            }

            return new Response(
                response.code(),
                response.message(),
                response.headers().toMultimap(),
                responseBodyToReturn,
                response.request().url().toString());
        }
    }
}
