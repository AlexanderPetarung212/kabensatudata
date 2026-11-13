<?php

namespace App\Enums;

enum DeviceCommandStatus: string
{
    case Requested = 'requested';
    case Authorized = 'authorized';
    case Queued = 'queued';
    case Sent = 'sent';
    case Delivered = 'delivered';
    case Executing = 'executing';
    case Completed = 'completed';
    case Failed = 'failed';
    case Expired = 'expired';
    case Cancelled = 'cancelled';
    case Rejected = 'rejected';

    public function isTerminal(): bool
    {
        return in_array($this, [
            self::Completed,
            self::Failed,
            self::Expired,
            self::Cancelled,
            self::Rejected,
        ], true);
    }
}
