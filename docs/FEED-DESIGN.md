# Moments feed layout

Android and iOS render these tokens. Colors come from the Telegram theme, including dark mode. The page background is plain. Icons are Telegram drawables, tinted with the theme.

Strings use `Moments_*` langpack keys, the same name as `R.string.Moments_*`. English is `values/strings.xml`. Simplified Chinese is `values-zh-rCN/strings.xml`. Release builds resolve them from `localization_en.bin` / `localization_zh_cn.bin`, not from `resources.arsc`. The server list is `docs/moments-langpack.json`.

## Feed list (Threads)

One row is a left avatar column and a right text column. A 1 px `divider` separates rows.

| Token | Value |
| --- | --- |
| Page background | `windowBackgroundWhite` |
| Cell padding | 12 dp horizontal, 10 dp top, 4 dp bottom |
| Avatar column | 48 dp wide |
| Avatar | 40 dp circle |
| Follow badge | 16 dp circle, bottom end of the avatar, `featuredStickers_addButton`, white “+”. Hidden when already following or the post is yours. No “Follow” text button |
| Name | 15 sp bold, `windowBackgroundWhiteBlackText`, one line, same row as the time |
| Time | 13 sp, `windowBackgroundWhiteGrayText`, 8 dp after the name |
| More | 32 dp, `ic_ab_other`, trailing end of the name row |
| Body | 15 sp, primary text, directly under the name |
| Fold | 5 lines, then “Full text” |
| Thread line | 2 dp wide, `divider`, under the avatar, only when the post quotes another post |
| Divider | 1 px, `divider` |

## Media

Media sits in the text column, 8 dp below the text. Corners are 12 dp.

| Count | Layout |
| --- | --- |
| 1 photo | full column width, height from the aspect ratio, clamped to 120–360 dp |
| 1 video | full column width, height `max(180 dp, width * 9 / 16)` |
| 2 or more | horizontal carousel. Each cell is 78% of the column and square, 8 dp gap, so the next image peeks. Swipe, do not use a grid. A swipe on the carousel, including the first and last photo, does not change the feed tab |

The list decodes each photo at the cell size, capped at 360 dp. `ImageLoader` reads a `W_H` filter as dp and multiplies by density. `800_800` on a 3x screen asks for 2400 px and leaves a 4032×3024 photo at sample size 1 (about 48 MB, ARGB_8888). Several of those during a fling get the process killed with no crash dialog. The cell filter is `imageFilter(cellPx, density)`.

List images are prefetched with Telegram `ImageReceiver`s, the same way `PeerStoriesView` preloads the next story: low file priority, and the visible cell reuses the bitmap when the url and filter match. The window is 3 posts ahead and 1 behind (1 ahead on a low-end device or while roaming), the avatar plus the first two images or a video cover, at most 2 downloads, cancelled when they leave the window. A fling faster than 4 screen heights per second starts nothing new. This follows Glide's `RecyclerViewPreloader` (same decode size, direction-aware, a few items ahead), Fresco's cancel-on-close prefetch, and Android's subsample-to-the-view guidance.

- https://bumptech.github.io/glide/int/recyclerview.html
- https://frescolib.org/docs/prefetching.html
- https://developer.android.com/topic/performance/graphics/load-bitmap

Following and Nearby show a `Moments_NewPosts` pill when a later fetch has a different first id. The pill does not insert rows. Tapping it, or pulling to refresh, loads them. Recommend does not poll.

Link, quote, poll, and file cards use `graySection`, 12 dp corners, padding 10 dp horizontal and 8 dp vertical.

## Actions

The row aligns with the text column. Icon, then the count. No text labels. Grey `dialogIcon`. A count of zero is hidden. There is no share count.

| Order | Drawable | Count |
| --- | --- | --- |
| Like | `media_like`, liked `media_like_active` at `#FF2E38` | `likes` |
| Comment | `msg_discussion` | `comments` |
| Repost | `msg_retry` | `reposts` |
| Share | `msg_send` | none |

Touch target 32 dp. 14 dp after each count. Save stays in the more menu (`msg_saved`), not on this row.

## Other icons

| Use | Drawable |
| --- | --- |
| Moments tab | `R.raw.tab_article` (`GlassTabView.TabAnimation.FEED`), same 24 dp Lottie as Contacts, Chats, and Settings. Always shown. |
| Notifications | `media_like` |
| Compose | `filled_fab_compose_32` on `FragmentFloatingButton` |
| Comment sheet expand | `msg_zoomin`, and `msg_zoomout` when full screen |
| Comment sheet close | `msg_close` |
| Comment like | `media_like` / `media_like_active` |
| Comment dislike | `msg_cancel`. Always shown. `PUT /v1/feed/comments/{id}/dislike` hides the comment for the viewer |
| Comment image | `msg_gallery` |
| Mention | `msg_mention` |
| Emoji | `input_smile` |

## Tabs and compose

| Token | Value |
| --- | --- |
| Tab row | 40 dp, 14 sp bold |
| Selected tab | `windowBackgroundWhiteBlackText` |
| Other tabs | `windowBackgroundWhiteGrayText` |
| Compose control | round `FragmentFloatingButton` |
| Compose fill | `featuredStickers_addButton`, pressed `featuredStickers_addButtonPressed` |
| Compose circle | 48 dp, in a 56 dp frame, bottom trailing |
| Compose margins | 20 dp from the trailing edge, 16 dp above the main tab bar |

The round blue compose button is the reference.

## Comment sheet (Douyin)

Tapping a post or the comment icon opens this sheet over the list. It does not push a new page.

| Token | Value |
| --- | --- |
| Top corners | 16 dp |
| Collapsed height | 72% of the screen |
| Expanded height | full screen below the status bar |
| Scrim | `#66000000` |
| Title | centred, 15 sp bold, `Moments_CommentCount`. The number is `total` from `GET /v1/feed/posts/{id}/comments?sort=hot&thread=1` |
| Sort | under the title, `Moments_Sort_Hot` / `Moments_Sort_Latest`. Latest uses `sort=latest&thread=1` |
| Long press | reply, copy, forward, report. Delete is shown to the commenter and the post author. Deleting a root comment warns with `Moments_DeleteThread` |
| Comment avatar | 32 dp |
| Nickname | 13 sp, `windowBackgroundWhiteGrayText` |
| Author tag | `Moments_Author` (`Author` / `作者`), `#FF2E38`, when `is_author` is true. `me.author` means the viewer wrote it and is not this tag |
| Comment text | 15 sp, primary |
| Comment image | 120 dp, 8 dp corners, when `attachment` is an image or sticker |
| Meta | 12 sp grey: relative time, ` · `, `card.ip_region_label` when present, ` · `, `Moments_Reply` |
| Reply indent | 48 dp start padding |
| Reply prefix | `Moments_ReplyTo` (`Reply @%s` / `回复 @%s`), name from `card.reply_to.display_name` |
| Collapsed replies | `—— ` plus `Moments_ShowReplies` (`Show %d replies` / `展开%d条回复`). Expanding calls `GET /v1/feed/comments/{id}/replies`, 20 at a time, and follows `cursor` |
| Like | heart and count from `stats.likes` and `me.liked`. `PUT` or `DELETE /v1/feed/comments/{id}/like` |
| Dislike | `msg_cancel`. `PUT /v1/feed/comments/{id}/dislike` hides that comment for the viewer. There is no public count |
| Input | one line, hint `Moments_CommentHint` (`Say something` / `发条评论，说说你的感受`), image, @, and emoji at the trailing end |
| Image or sticker | `POST` body `media_keys` is one tus key. `sticker: true` marks a sticker. The gallery uploads one photo. The emoji button picks a sticker and uploads its file the same way |

`card.media[0]` is the comment image. `avatar_url` is used when present; otherwise the avatar is `media_base_url` plus `avatar_key`. A hot cursor is not reused as a time cursor.
