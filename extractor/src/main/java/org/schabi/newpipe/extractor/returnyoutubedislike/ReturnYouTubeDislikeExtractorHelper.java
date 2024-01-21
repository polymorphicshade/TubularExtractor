package org.schabi.newpipe.extractor.returnyoutubedislike;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.IOException;

public final class ReturnYouTubeDislikeExtractorHelper {

    private ReturnYouTubeDislikeExtractorHelper() {
    }

    @SuppressWarnings("CheckStyle")
    public static ReturnYouTubeDislikeInfo getInfo(
            final StreamInfo streamInfo,
            final ReturnYouTubeDislikeApiSettings apiSettings) {
        if (!streamInfo.getUrl().startsWith("https://www.youtube.com")
                || apiSettings.apiUrl == null
                || apiSettings.apiUrl.isEmpty()) {
            return null;
        }

        final String url = apiSettings.apiUrl + "votes?videoId=" + streamInfo.getId();

        JsonObject response = null;

        try {
            final String responseBody =
                    NewPipe.getDownloader().get(url).responseBody();

            response = JsonParser.object().from(responseBody);
        } catch (ReCaptchaException | IOException | JsonParserException e) {
            // ignored
        }

        if (response == null) {
            return null;
        }

        final int likes = response.getInt("likes", 0);
        final int dislikes = response.getInt("dislikes", 0);
        final double rating = response.getDouble("rating", 0);
        final int viewCount = response.getInt("viewCount", 0);
        final boolean deleted = response.getBoolean("deleted", false);

        return new ReturnYouTubeDislikeInfo(likes, dislikes, rating, viewCount, deleted);
    }
}
