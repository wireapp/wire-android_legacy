package com.waz.zclient.views;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class HeaderFooterAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static int HEADER_VIEW_TYPE = -1000, FOOTER_VIEW_TYPE = -1001;

    private RecyclerView.Adapter<T> wrapped;

    @Nullable private View header;
    @Nullable private View footer;

    public HeaderFooterAdapter(RecyclerView.Adapter<T> wrapped) {
        this.wrapped = wrapped;
    }

    public void setHeader(@Nullable View view) {
        boolean oldHeaderEmpty = header == null;
        header = view;

        if (view == null && !oldHeaderEmpty) notifyItemRemoved(0);
        else if (oldHeaderEmpty) notifyItemInserted(0);
        else notifyItemChanged(0);
    }

    public void setFooter(@Nullable View view) {
        boolean oldFooterEmpty = footer == null;
        footer = view;

        if (view == null && !oldFooterEmpty) notifyItemRemoved(getItemCount() - 1);
        else if (oldFooterEmpty) notifyItemInserted(getItemCount());
        else notifyItemChanged(getItemCount() - 1);
    }

    private boolean isHeader(int position) {
        return header != null && position == 0;
    }

    private boolean isFooter(int position) {
        return footer != null && position == getItemCount() - 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == HEADER_VIEW_TYPE) return new FrameViewHolder(new FrameLayout(parent.getContext()));
        else if (viewType == FOOTER_VIEW_TYPE) return new FrameViewHolder(new FrameLayout(parent.getContext()));
        else return wrapped.onCreateViewHolder(parent, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (isHeader(position)) ((FrameViewHolder) holder).setView(header);
        else if (isFooter(position)) ((FrameViewHolder) holder).setView(footer);
        else wrapped.onBindViewHolder((T) holder, position);
    }

    @Override
    public int getItemViewType(int position) {
        if (isHeader(position)) return HEADER_VIEW_TYPE;
        else if (isFooter(position)) return FOOTER_VIEW_TYPE;
        else if (header != null) return wrapped.getItemViewType(position - 1);
        else return wrapped.getItemViewType(position);
    }

    @Override
    public int getItemCount() {
        if (header != null && footer != null) return wrapped.getItemCount() + 2;
        else if (header == null && footer == null) return wrapped.getItemCount();
        else return wrapped.getItemCount() + 1;
    }


    static class FrameViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout frame;

        public FrameViewHolder(FrameLayout frame) {
            super(frame);
            this.frame = frame;
        }

        public void setView(View view) {
            frame.removeAllViews();
            frame.addView(view);
        }

    }

}
