<?php

namespace App\Enums;

enum ManagedDeviceStatus: string
{
    case Online = 'online';
    case Idle = 'idle';
    case Offline = 'offline';
    case Revoked = 'revoked';
}
