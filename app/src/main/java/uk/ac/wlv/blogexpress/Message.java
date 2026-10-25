package uk.ac.wlv.blogexpress;

import java.io.File;

/**
 * Model class representing one blog post (message) stored in the SQLite database.
 */
public class Message {

    private long id = -1;
    private String title;
    private String content;
    private String imagePath;   // absolute path of the attached photo, or null
    private long createdAt;
    private long updatedAt;
    private boolean uploaded;   // true once published to WordPress.com
    private String remoteUrl;   // link to the published online post
    private boolean draft;      // true when saved as a draft

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public boolean isUploaded() { return uploaded; }
    public void setUploaded(boolean uploaded) { this.uploaded = uploaded; }

    public String getRemoteUrl() { return remoteUrl; }
    public void setRemoteUrl(String remoteUrl) { this.remoteUrl = remoteUrl; }

    public boolean isDraft() { return draft; }
    public void setDraft(boolean draft) { this.draft = draft; }

    /** Returns true if this post has a photo that still exists on the device. */
    public boolean hasImage() {
        return imagePath != null && new File(imagePath).exists();
    }
}