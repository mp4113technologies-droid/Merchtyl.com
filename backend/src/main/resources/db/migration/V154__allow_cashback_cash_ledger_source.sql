alter table cash_ledger_entries
    drop constraint ck_cash_ledger_entries_source_direction,
    drop constraint ck_cash_ledger_entries_source_type;

alter table cash_ledger_entries
    add constraint ck_cash_ledger_entries_source_type
        check (source_type in (
            'SESSION_OPENING_FLOAT',
            'SALE_CASH_RECEIPT',
            'SALE_CHANGE_GIVEN',
            'LOTTERY_SALE_CASH',
            'LOTTERY_PAYOUT_CASH',
            'LOTTERY_PAYOUT_REVERSAL',
            'LOTTERY_SALE_CANCELLATION_CASH',
            'DEPOSIT_PAYOUT',
            'CASHBACK_PAYOUT',
            'CASH_REFUND',
            'CASH_MOVEMENT',
            'SESSION_CLOSE_ADJUSTMENT',
            'SESSION_CLOSE_TILL_REMOVAL',
            'TILL_RESTORE'
        )),
    add constraint ck_cash_ledger_entries_source_direction
        check (
            (source_type in (
                'SESSION_OPENING_FLOAT',
                'SALE_CASH_RECEIPT',
                'LOTTERY_SALE_CASH',
                'LOTTERY_PAYOUT_REVERSAL',
                'TILL_RESTORE'
            ) and direction = 'IN')
            or
            (source_type in (
                'SALE_CHANGE_GIVEN',
                'LOTTERY_PAYOUT_CASH',
                'LOTTERY_SALE_CANCELLATION_CASH',
                'DEPOSIT_PAYOUT',
                'CASHBACK_PAYOUT',
                'CASH_REFUND',
                'SESSION_CLOSE_TILL_REMOVAL'
            ) and direction = 'OUT')
            or source_type in ('CASH_MOVEMENT', 'SESSION_CLOSE_ADJUSTMENT')
        );
