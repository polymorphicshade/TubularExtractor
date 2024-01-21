package org.schabi.newpipe.extractor.returnyoutubedislike;

import java.io.Serializable;

public class ReturnYouTubeDislikeInfo implements Serializable {
    public long likes;
    public long dislikes;
    public double rating;
    public long viewCount;
    public boolean deleted;

    public ReturnYouTubeDislikeInfo(final long likes, final long dislikes, final double rating,
                                    final long viewCount, final boolean deleted) {
        this.likes = likes;
        this.dislikes = dislikes;
        this.rating = rating;
        this.viewCount = viewCount;
        this.deleted = deleted;
    }
}
