package org.telegram.ui.Feed;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.feed.FeedContract;
import org.telegram.messenger.feed.FeedController;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;

/**
 * Profile posts tab. SharedMediaLayout hosts this view the same way it hosts
 * the gifts and saved-message containers. Follow and followers stay at the
 * top. Posts, paging, likes, and comments stay in the tab.
 * Top padding is the only inset that moves this page clear of the profile
 * name. The avatar and title layout are left alone.
 */
public class FeedProfilePage extends FrameLayout implements FeedController.Listener {

    private final FeedContract.QueryGeneration generation = new FeedContract.QueryGeneration();
    private final BaseFragment fragment;
    private final int account;
    private final String profileId;
    private FeedListView list;
    private int token;
    private TextView followingView;
    private TextView followersView;
    private FeedPostCell.Delegate postDelegate;

    public FeedProfilePage(Context context, BaseFragment fragment, long dialogId) {
        super(context);
        this.fragment = fragment;
        account = fragment.getCurrentAccount();
        profileId = String.valueOf(dialogId);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        boolean self = dialogId == UserConfig.getInstance(account).getClientUserId();
        followingView = addLink(column, entry(false, null), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.FOLLOWING, profileId)));
        followersView = addLink(column, entry(true, null), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.FOLLOWERS, profileId)));
        if (self) {
            addLink(column, LocaleController.getString(R.string.Moments_Profile_Saved), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.SAVED)));
            addLink(column, LocaleController.getString(R.string.Moments_Profile_Liked), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.LIKED)));
        } else {
            addLink(column, LocaleController.getString(R.string.Moments_Follow), () -> controller().follow(profileId, true));
            addLink(column, LocaleController.getString(R.string.Moments_Mute), () -> controller().mute(profileId, true));
            addLink(column, LocaleController.getString(R.string.Moments_Report), () -> FeedReportSheet.open(fragment, "user", profileId));
        }
        addLink(column, LocaleController.getString("Moments_Profile_OpenPosts", R.string.Moments_Profile_OpenPosts), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.PROFILE, profileId)));
        postDelegate = new FeedPostCell.Delegate() {
            @Override
            public void onOpen(FeedModels.Post post) {
                FeedUi.openEntry(fragment, post, FeedContract.ENTRY_PROFILE, false);
            }

            @Override
            public void onLike(FeedModels.Post post) {
                controller().toggleLike(post);
            }

            @Override
            public void onComment(FeedModels.Post post) {
                FeedUi.openEntry(fragment, post, FeedContract.ENTRY_COMMENT, true);
            }

            @Override
            public void onRepost(FeedModels.Post post) {
                FeedPostActions.showRepostMenu(fragment, post, FeedProfilePage.this);
            }

            @Override
            public void onShare(FeedModels.Post post) {
                FeedPostActions.showShareMenu(fragment, post, FeedProfilePage.this);
            }

            @Override
            public void onSave(FeedModels.Post post) {
                controller().toggleSave(post);
            }

            @Override
            public void onMore(FeedModels.Post post, View anchor) {
                FeedPostActions.showMoreMenu(fragment, post, anchor);
            }

            @Override
            public void onFollow(FeedModels.Post post) {
                if (post == null || post.author == null) {
                    return;
                }
                boolean next = !post.following;
                post.following = next;
                controller().follow(post.author.profileId, next);
            }

            @Override
            public void onAuthor(FeedModels.Post post) {
                if (post != null) {
                    FeedUi.openProfile(fragment, post.author);
                }
            }

            @Override
            public void onVote(FeedModels.Post post, int option) {
                if (post == null || post.poll == null || post.poll.options == null || option < 0 || option >= post.poll.options.size()) {
                    return;
                }
                JSONArray options = new JSONArray();
                options.put(post.poll.options.get(option).id);
                controller().vote(post.id, options, false, error -> {
                    if (list != null) {
                        list.refresh();
                    }
                });
            }

            @Override
            public void onMedia(FeedModels.Post post, int index) {
                FeedMediaViewer.open(fragment, post, index);
            }
        };
        list = new FeedListView(context, new FeedListView.Host() {
            @Override
            public ArrayList<FeedModels.Post> posts() {
                return controller().posts(FeedContract.profilePostsStream(profileId));
            }

            @Override
            public void onRefresh() {
                controller().loadProfilePosts(profileId, true);
            }

            @Override
            public void onLoadMore() {
                controller().loadProfilePosts(profileId, false);
            }

            @Override
            public void onPrefetchPage() {
                if (!controller().profilePostsLoading(profileId) && FeedContract.normalizeCursor(controller().cursor(FeedContract.profilePostsStream(profileId))).length() > 0) {
                    controller().loadProfilePosts(profileId, false);
                }
            }

            @Override
            public boolean loading() {
                return controller().profilePostsLoading(profileId) && posts().isEmpty();
            }

            @Override
            public boolean failed() {
                return controller().profilePostsFailed(profileId);
            }

            @Override
            public boolean hasMore() {
                return FeedContract.normalizeCursor(controller().cursor(FeedContract.profilePostsStream(profileId))).length() > 0;
            }

            @Override
            public FeedPostCell.Delegate delegate() {
                return postDelegate;
            }

            @Override
            public int account() {
                return account;
            }

            @Override
            public boolean showFollow() {
                return false;
            }

            @Override
            public String emptyText() {
                return LocaleController.getString(R.string.Moments_Empty_Recommend);
            }

            @Override
            public String emptyAction() {
                return null;
            }

            @Override
            public Runnable emptyClick() {
                return null;
            }
        });
        column.addView(list, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    /** Clears the tab strip and the profile name. Does not move the avatar or the title. */
    public void setContentPaddingTop(int px) {
        int next = Math.max(0, px);
        if (getPaddingTop() != next) {
            setPadding(getPaddingLeft(), next, getPaddingRight(), getPaddingBottom());
        }
    }

    public void setListBottomInset(int px) {
        if (list != null) {
            list.applyBottomInset(Math.max(0, px));
        }
    }

    public RecyclerListView getListView() {
        return list == null ? null : list.listView;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        controller().addListener(this);
        loadCounts();
        if (list != null) {
            list.refresh();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        generation.next();
        controller().removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onChanged() {
        if (list != null) {
            list.refresh();
        }
    }

    @Override
    public void onPostUpdated(String postId) {
        if (list != null) {
            list.notifyPost(postId);
        }
    }

    private FeedController controller() {
        return FeedController.getInstance(account);
    }

    private void loadCounts() {
        token = generation.next();
        FeedController controller = controller();
        String following = FeedContract.relationPath(profileId, false, "");
        String followers = FeedContract.relationPath(profileId, true, "");
        if (following != null) {
            controller.api.getJson(following, (json, error) -> {
                if (!generation.accept(token)) {
                    return;
                }
                followingView.setText(entry(false, FeedContract.parseRelationPage(json == null ? null : json.toString(), error, false)));
            });
        }
        if (followers != null) {
            controller.api.getJson(followers, (json, error) -> {
                if (!generation.accept(token)) {
                    return;
                }
                followersView.setText(entry(true, FeedContract.parseRelationPage(json == null ? null : json.toString(), error, true)));
            });
        }
    }

    private static String entry(boolean followers, FeedContract.RelationPage page) {
        if (page != null && page.hidden) {
            return LocaleController.getString(followers ? R.string.Moments_FollowersPrivate : R.string.Moments_FollowingPrivate);
        }
        if (page == null || page.failed || page.total < 0) {
            return LocaleController.getString(followers ? R.string.Moments_FollowersEntry : R.string.Moments_FollowingEntry);
        }
        if (followers) {
            return LocaleController.formatString(R.string.Moments_FollowersWithCount, page.total);
        }
        return LocaleController.formatString(R.string.Moments_FollowingWithCount, page.total);
    }

    private TextView addLink(LinearLayout column, String title, Runnable click) {
        TextView view = new TextView(getContext());
        view.setText(title);
        view.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        view.setTextSize(16);
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        view.setOnClickListener(v -> click.run());
        column.addView(view);
        return view;
    }
}
