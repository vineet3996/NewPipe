package org.schabi.newpipe.fragments.list.comments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.views.NewPipeRecyclerView;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class CommentsFragment extends BaseListInfoFragment<CommentsInfoItem, CommentsInfo> {
    private final CompositeDisposable disposables = new CompositeDisposable();

    private TextView emptyStateDesc;

    private View repliesOverlay;
    private ImageButton repliesBackButton;
    private TextView repliesOverlayTitle;

    private OnBackPressedCallback backCallback;

    public static CommentsFragment getInstance(final int serviceId, final String url,
                                               final String name) {
        final CommentsFragment instance = new CommentsFragment();
        instance.setInitialData(serviceId, url, name);
        return instance;
    }

    public CommentsFragment() {
        super(UserAction.REQUESTED_COMMENTS);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        emptyStateDesc = rootView.findViewById(R.id.empty_state_desc);

        repliesOverlay = rootView.findViewById(R.id.replies_overlay);
        repliesBackButton = rootView.findViewById(R.id.replies_back_button);
        repliesOverlayTitle = rootView.findViewById(R.id.replies_overlay_title);

        if (repliesBackButton != null) {
            repliesBackButton.setOnClickListener(v -> hideRepliesOverlay());
        }

        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (repliesOverlay != null && repliesOverlay.getVisibility() == View.VISIBLE) {
                    hideRepliesOverlay();
                } else {
                    // let system handle back - disable this callback
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), backCallback);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_comments, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
        if (backCallback != null) {
            backCallback.setEnabled(false);
            backCallback.remove();
            backCallback = null;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<ListExtractor.InfoItemsPage<CommentsInfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreCommentItems(serviceId, currentInfo, currentNextPage);
    }

    @Override
    protected Single<CommentsInfo> loadResult(final boolean forceLoad) {
        return ExtractorHelper.getCommentsInfo(serviceId, url, forceLoad);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull final CommentsInfo result) {
        super.handleResult(result);

        emptyStateDesc.setText(
                result.isCommentsDisabled()
                        ? R.string.comments_are_disabled
                        : R.string.no_comments);

        ViewUtils.slideUp(requireView(), 120, 150, 0.06f);
        disposables.clear();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(final String title) { }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) { }

    @Override
    protected ItemViewMode getItemViewMode() {
        return ItemViewMode.LIST;
    }

    public boolean scrollToComment(final CommentsInfoItem comment) {
        final int position = infoListAdapter.getItemsList().indexOf(comment);
        if (position < 0) {
            return false;
        }

        itemsList.scrollToPosition(position);
        return true;
    }

    public void showRepliesOverlay(final CommentsInfoItem comment) {
        if (repliesOverlay == null) {
            return;
        }

        // create fragment and put it inside replies_fragment_container
        final CommentRepliesFragment repliesFragment = new CommentRepliesFragment(comment);
        repliesOverlayTitle.setText(Localization
                .replyCount(requireContext(), comment.getReplyCount()));

        // show overlay first so the container exists in the view hierarchy
        repliesOverlay.setVisibility(View.VISIBLE);

        // disable underlying list focus to avoid DPAD interactions leaking
        if (itemsList instanceof NewPipeRecyclerView) {
            ((NewPipeRecyclerView) itemsList).setFocusScrollAllowed(false);
        }

        // load the replies fragment as a child fragment into the overlay's container
        try {
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.replies_fragment_container, repliesFragment)
                    .commitAllowingStateLoss();
        } catch (final Exception e) {
            // swallow any exception to avoid crashing the comments list when overlay fails
            // we'll keep the overlay visible but without the child fragment
        }
    }

    public void hideRepliesOverlay() {
        // re-enable scroll focus
        if (itemsList instanceof NewPipeRecyclerView) {
            ((NewPipeRecyclerView) itemsList).setFocusScrollAllowed(true);
        }
        if (repliesOverlay == null || repliesOverlay.getVisibility() != View.VISIBLE) {
            return;
        }

        // remove any child fragment hosted in the replies fragment container
        try {
            final Fragment child = getChildFragmentManager().findFragmentById(
                    R.id.replies_fragment_container);
            if (child != null) {
                getChildFragmentManager().beginTransaction()
                        .remove(child)
                        .commitAllowingStateLoss();
            }
        } catch (final Exception e) {
            // ignore removal errors
        }

        repliesOverlay.setVisibility(View.INVISIBLE);
    }
}
