UPDATE stock_counts
SET status = 'SAVED',
    reviewed_by_user_id = NULL,
    reviewed_at = NULL,
    review_notes = NULL,
    post_notes = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'IN_REVIEW';
