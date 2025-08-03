package org.schabi.newpipe.extractor.services.youtube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.schabi.newpipe.extractor.ExtractorAsserts.assertContains;
import static org.schabi.newpipe.extractor.ExtractorAsserts.assertGreater;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;
import static org.schabi.newpipe.extractor.comments.CommentsInfoItem.UNKNOWN_REPLY_COUNT;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.DefaultSimpleExtractorTest;
import org.schabi.newpipe.extractor.services.DefaultTests;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeCommentsExtractor;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class YoutubeCommentsExtractorTest {

    abstract static class Base extends DefaultSimpleExtractorTest<YoutubeCommentsExtractor>
        implements InitYoutubeTest {

        @Override
        protected YoutubeCommentsExtractor createExtractor() throws Exception {
            return (YoutubeCommentsExtractor) YouTube.getCommentsExtractor(extractorUrl());
        }

        protected abstract String extractorUrl();
    }

    /**
     * Test a "normal" YouTube
     */
    public static class Thomas extends Base {
        private static final String URL = "https://www.youtube.com/watch?v=D00Au7k3i6o";
        private static final String commentContent = "Category: Education";

        @Override
        protected String extractorUrl() {
            return URL;
        }

        @Test
        void testGetComments() throws IOException, ExtractionException {
            assertTrue(getCommentsHelper(extractor()));
        }

        private boolean getCommentsHelper(final YoutubeCommentsExtractor extractor) throws IOException, ExtractionException {
            InfoItemsPage<CommentsInfoItem> comments = extractor.getInitialPage();
            boolean result = findInComments(comments, commentContent);

            while (comments.hasNextPage() && !result) {
                comments = extractor.getPage(comments.getNextPage());
                result = findInComments(comments, commentContent);
            }

            return result;
        }

        @Test
        void testGetCommentsFromCommentsInfo() throws IOException, ExtractionException {
            assertTrue(getCommentsFromCommentsInfoHelper(URL));
        }

        private boolean getCommentsFromCommentsInfoHelper(final String url) throws IOException, ExtractionException {
            final CommentsInfo commentsInfo = CommentsInfo.getInfo(url);

            assertEquals("Comments", commentsInfo.getName());
            boolean result = findInComments(commentsInfo.getRelatedItems(), commentContent);

            Page nextPage = commentsInfo.getNextPage();
            InfoItemsPage<CommentsInfoItem> moreItems = new InfoItemsPage<>(null, nextPage, null);
            while (moreItems.hasNextPage() && !result) {
                moreItems = CommentsInfo.getMoreItems(YouTube, commentsInfo, nextPage);
                result = findInComments(moreItems.getItems(), commentContent);
                nextPage = moreItems.getNextPage();
            }
            return result;
        }

        @Test
        void testGetCommentsAllData() throws IOException, ExtractionException {
            final InfoItemsPage<CommentsInfoItem> comments = extractor().getInitialPage();
            assertTrue(extractor().getCommentsCount() > 5); // at least 5 comments

            DefaultTests.defaultTestListOfItems(YouTube, comments.getItems(), comments.getErrors());
            for (final CommentsInfoItem c : comments.getItems()) {
                assertFalse(Utils.isBlank(c.getUploaderUrl()));
                assertFalse(Utils.isBlank(c.getUploaderName()));
                YoutubeTestsUtils.testImages(c.getUploaderAvatars());
                assertFalse(Utils.isBlank(c.getCommentId()));
                assertFalse(Utils.isBlank(c.getCommentText().getContent()));
                assertFalse(Utils.isBlank(c.getName()));
                assertFalse(Utils.isBlank(c.getTextualUploadDate()));
                assertNotNull(c.getUploadDate());
                YoutubeTestsUtils.testImages(c.getThumbnails());
                assertFalse(Utils.isBlank(c.getUrl()));
                assertTrue(c.getLikeCount() >= 0);
            }
        }

        private boolean findInComments(final InfoItemsPage<CommentsInfoItem> comments, final String comment) {
            return findInComments(comments.getItems(), comment);
        }

        private boolean findInComments(final List<CommentsInfoItem> comments, final String comment) {
            for (final CommentsInfoItem c : comments) {
                if (c.getCommentText().getContent().contains(comment)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Test a video with an empty comment
     */
    public static class EmptyComment extends Base {
        private final static String URL = "https://www.youtube.com/watch?v=VM_6n762j6M";

        @Override
        protected String extractorUrl() {
            return URL;
        }

        @Test
        void testGetCommentsAllData() throws IOException, ExtractionException {
            final InfoItemsPage<CommentsInfoItem> comments = extractor().getInitialPage();

            DefaultTests.defaultTestListOfItems(YouTube, comments.getItems(), comments.getErrors());
            for (final CommentsInfoItem c : comments.getItems()) {
                assertFalse(Utils.isBlank(c.getUploaderUrl()));
                assertFalse(Utils.isBlank(c.getUploaderName()));
                YoutubeTestsUtils.testImages(c.getUploaderAvatars());
                assertFalse(Utils.isBlank(c.getCommentId()));
                assertFalse(Utils.isBlank(c.getName()));
                assertFalse(Utils.isBlank(c.getTextualUploadDate()));
                assertNotNull(c.getUploadDate());
                YoutubeTestsUtils.testImages(c.getThumbnails());
                assertFalse(Utils.isBlank(c.getUrl()));
                assertTrue(c.getLikeCount() >= 0);
                if (c.getCommentId().equals("Ugga_h1-EXdHB3gCoAEC")) { // comment without text
                    assertTrue(Utils.isBlank(c.getCommentText().getContent()));
                } else {
                    assertFalse(Utils.isBlank(c.getCommentText().getContent()));
                }
            }
        }

    }

    public static class HeartedByCreator extends Base {
        private final static String URL = "https://www.youtube.com/watch?v=tR11b7uh17Y";

        @Override
        protected String extractorUrl() {
            return URL;
        }

        @Test
        void testGetCommentsAllData() throws IOException, ExtractionException {
            final InfoItemsPage<CommentsInfoItem> comments = extractor().getInitialPage();

            DefaultTests.defaultTestListOfItems(YouTube, comments.getItems(), comments.getErrors());

            boolean heartedByUploader = false;

            for (final CommentsInfoItem c : comments.getItems()) {
                assertFalse(Utils.isBlank(c.getUploaderUrl()));
                assertFalse(Utils.isBlank(c.getUploaderName()));
                YoutubeTestsUtils.testImages(c.getUploaderAvatars());
                assertFalse(Utils.isBlank(c.getCommentId()));
                assertFalse(Utils.isBlank(c.getName()));
                assertFalse(Utils.isBlank(c.getTextualUploadDate()));
                assertNotNull(c.getUploadDate());
                YoutubeTestsUtils.testImages(c.getThumbnails());
                assertFalse(Utils.isBlank(c.getUrl()));
                assertTrue(c.getLikeCount() >= 0);
                assertFalse(Utils.isBlank(c.getCommentText().getContent()));
                if (c.isHeartedByUploader()) {
                    heartedByUploader = true;
                }
            }
            assertTrue(heartedByUploader, "No comments was hearted by uploader");

        }
    }

    public static class Pinned extends Base {
        private final static String URL = "https://www.youtube.com/watch?v=bjFtFMilb34";

        @Override
        protected String extractorUrl() {
            return URL;
        }

        @Test
        void testGetCommentsAllData() throws IOException, ExtractionException {
            final InfoItemsPage<CommentsInfoItem> comments = extractor().getInitialPage();

            DefaultTests.defaultTestListOfItems(YouTube, comments.getItems(), comments.getErrors());

            for (final CommentsInfoItem c : comments.getItems()) {
                assertFalse(Utils.isBlank(c.getUploaderUrl()));
                assertFalse(Utils.isBlank(c.getUploaderName()));
                YoutubeTestsUtils.testImages(c.getUploaderAvatars());
                assertFalse(Utils.isBlank(c.getCommentId()));
                assertFalse(Utils.isBlank(c.getName()));
                assertFalse(Utils.isBlank(c.getTextualUploadDate()));
                assertNotNull(c.getUploadDate());
                YoutubeTestsUtils.testImages(c.getThumbnails());
                assertFalse(Utils.isBlank(c.getUrl()));
                assertTrue(c.getLikeCount() >= 0);
                assertFalse(Utils.isBlank(c.getCommentText().getContent()));
            }

            assertTrue(comments.getItems().get(0).isPinned(), "First comment isn't pinned");
        }
    }

    /**
     * Checks if the likes/votes are handled correctly<br/>
     * A pinned comment with >15K likes is used for the test
     */
    public static class LikesVotes extends Base {
        private final static String URL = "https://www.youtube.com/watch?v=QqsLTNkzvaY";

        @Override
        protected String extractorUrl() {
            return URL;
        }

        @Test
        void testGetCommentsFirst() throws IOException, ExtractionException {
            final InfoItemsPage<CommentsInfoItem> comments = extractor().getInitialPage();

            DefaultTests.defaultTestListOfItems(YouTube, comments.getItems(), comments.getErrors());

            final CommentsInfoItem pinnedComment = comments.getItems().get(0);

            assertTrue(pinnedComment.isPinned(), "First comment isn't pinned");
            assertTrue(pinnedComment.getLikeCount() > 0, "The first pinned comment has no likes");
            assertFalse(Utils.isBlank(pinnedComment.getTextualLikeCount()), "The first pinned comment has no vote count");
        }
    }

    /**
     * Checks if the vote count works localized<br/>
     * A pinned comment with >15K likes is used for the test
     */
    public static class LocalizedVoteCount extends Base {
        private final static String URL = "https://www.youtube.com/watch?v=QqsLTNkzvaY";

        @Override
        protected String extractorUrl() {
            return URL;
        }

        @Override
        protected void fetchExtractor(final YoutubeCommentsExtractor extractor) throws Exception {
            // Force non english local here
            extractor.forceLocalization(Localization.fromLocale(Locale.GERMANY));
            super.fetchExtractor(extractor);
        }

        @Test
        void testGetCommentsFirst() throws IOException, ExtractionException {
            final InfoItemsPage<CommentsInfoItem> comments = extractor().getInitialPage();

            DefaultTests.defaultTestListOfItems(YouTube, comments.getItems(), comments.getErrors());

            final CommentsInfoItem pinnedComment = comments.getItems().get(0);

            assertTrue(pinnedComment.isPinned(), "First comment isn't pinned");
            assertFalse(Utils.isBlank(pinnedComment.getTextualLikeCount()), "The first pinned comment has no vote count");
        }
    }

    public static class RepliesTest extends Base {
        private final static String URL = "https://www.youtube.com/watch?v=xaQJbozY_Is";

        @Override
        protected String extractorUrl() {
            return URL;
        }

        @Test
        void testGetCommentsFirstReplies() throws IOException, ExtractionException {
            final InfoItemsPage<CommentsInfoItem> comments = extractor().getInitialPage();

            DefaultTests.defaultTestListOfItems(YouTube, comments.getItems(), comments.getErrors());

            final CommentsInfoItem firstComment = comments.getItems().get(0);

            assertTrue(firstComment.isPinned(), "First comment isn't pinned");

            final InfoItemsPage<CommentsInfoItem> replies = extractor().getPage(firstComment.getReplies());

            assertEquals("First", replies.getItems().get(0).getCommentText().getContent(),
                    "First reply comment did not match");
        }

        @Test
        public void testGetCommentsReplyCount() throws IOException, ExtractionException {
            final InfoItemsPage<CommentsInfoItem> comments = extractor().getInitialPage();

            DefaultTests.defaultTestListOfItems(YouTube, comments.getItems(), comments.getErrors());

            final CommentsInfoItem firstComment = comments.getItems().get(0);

            assertNotEquals(UNKNOWN_REPLY_COUNT, firstComment.getReplyCount(), "Could not get the reply count of the first comment");
            assertGreater(300, firstComment.getReplyCount());
        }

        @Test
        public void testCommentsCount() throws IOException, ExtractionException {
            assertTrue(extractor().getCommentsCount() > 18800);
        }
    }

    public static class ChannelOwnerTest extends Base {
        private final static String URL = "https://www.youtube.com/watch?v=bem4adjGKjE";

        @Override
        protected String extractorUrl() {
            return URL;
        }

        @Test
        void testGetCommentsAllData() throws IOException, ExtractionException {
            final InfoItemsPage<CommentsInfoItem> comments = extractor().getInitialPage();

            DefaultTests.defaultTestListOfItems(YouTube, comments.getItems(), comments.getErrors());

            boolean channelOwner = false;

            for (final CommentsInfoItem c : comments.getItems()) {
                assertFalse(Utils.isBlank(c.getUploaderUrl()));
                assertFalse(Utils.isBlank(c.getUploaderName()));
                YoutubeTestsUtils.testImages(c.getUploaderAvatars());
                assertFalse(Utils.isBlank(c.getCommentId()));
                assertFalse(Utils.isBlank(c.getName()));
                assertFalse(Utils.isBlank(c.getTextualUploadDate()));
                assertNotNull(c.getUploadDate());
                YoutubeTestsUtils.testImages(c.getThumbnails());
                assertFalse(Utils.isBlank(c.getUrl()));
                assertTrue(c.getLikeCount() >= 0);
                assertFalse(Utils.isBlank(c.getCommentText().getContent()));
                if (c.isChannelOwner()) {
                    channelOwner = true;
                }
            }
            assertTrue(channelOwner, "No comments was made by the channel owner");

        }
    }


    public static class CreatorReply extends Base {
        private final static String URL = "https://www.youtube.com/watch?v=bem4adjGKjE";

        @Override
        protected String extractorUrl() {
            return URL;
        }

        @Test
        void testGetCommentsAllData() throws IOException, ExtractionException {
            final InfoItemsPage<CommentsInfoItem> comments = extractor().getInitialPage();

            DefaultTests.defaultTestListOfItems(YouTube, comments.getItems(), comments.getErrors());

            boolean creatorReply = false;

            for (final CommentsInfoItem c : comments.getItems()) {
                assertFalse(Utils.isBlank(c.getUploaderUrl()));
                assertFalse(Utils.isBlank(c.getUploaderName()));
                YoutubeTestsUtils.testImages(c.getUploaderAvatars());
                assertFalse(Utils.isBlank(c.getCommentId()));
                assertFalse(Utils.isBlank(c.getName()));
                assertFalse(Utils.isBlank(c.getTextualUploadDate()));
                assertNotNull(c.getUploadDate());
                YoutubeTestsUtils.testImages(c.getThumbnails());
                assertFalse(Utils.isBlank(c.getUrl()));
                assertTrue(c.getLikeCount() >= 0);
                assertFalse(Utils.isBlank(c.getCommentText().getContent()));
                if (c.hasCreatorReply()) {
                    creatorReply = true;
                }
            }
            assertTrue(creatorReply, "No comments was replied to by creator");

        }
    }


    public static class Formatting extends Base {

        private final static String URL = "https://www.youtube.com/watch?v=zYpyS2HaZHM";

        @Override
        protected String extractorUrl() {
            return URL;
        }

        @Test
        public void testGetCommentsFormatting() throws IOException, ExtractionException {
            final InfoItemsPage<CommentsInfoItem> comments = extractor().getInitialPage();

            DefaultTests.defaultTestListOfItems(YouTube, comments.getItems(), comments.getErrors());

            final CommentsInfoItem firstComment = comments.getItems().get(0);

            assertContains("<s>", firstComment.getCommentText().getContent());
            assertContains("<b>", firstComment.getCommentText().getContent());
        }
    }
}
