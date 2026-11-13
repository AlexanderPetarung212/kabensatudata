<?php

namespace App\Services\CommandCenter;

use App\Enums\DeviceCommandStatus;
use App\Models\DeviceCommand;
use App\Models\ManagedDevice;
use App\Models\User;
use Illuminate\Auth\Access\AuthorizationException;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

class DeviceCommandService
{
    public function __construct(
        private readonly CommandCenterAuditService $audit,
    ) {
    }

    public function create(
        ManagedDevice $device,
        User $user,
        string $name,
        array $payload,
        ?string $reason,
        ?string $idempotencyKey = null,
    ): DeviceCommand {
        $name = strtoupper($name);
        $definition = config("command-center.commands.{$name}");

        if (!$definition) {
            throw ValidationException::withMessages([
                'name' => 'Command tidak didukung.',
            ]);
        }

        if (!$user->can($definition['permission'])) {
            throw new AuthorizationException('Anda tidak memiliki permission untuk command ini.');
        }

        $requiredCapability = $definition['capability'] ?? null;
        if ($requiredCapability && !in_array($requiredCapability, $device->capabilities ?? [], true)) {
            throw ValidationException::withMessages([
                'name' => "Perangkat belum mendukung capability {$requiredCapability}.",
            ]);
        }

        if (($definition['sensitive'] ?? false) && blank($reason)) {
            throw ValidationException::withMessages([
                'reason' => 'Alasan wajib diisi untuk tindakan sensitif.',
            ]);
        }

        $validatedPayload = Validator::make(
            $payload,
            $definition['payload_rules'] ?? [],
        )->validate();

        $idempotencyKey ??= (string) Str::uuid();

        $existing = DeviceCommand::query()
            ->where('managed_device_id', $device->id)
            ->where('idempotency_key', $idempotencyKey)
            ->first();

        if ($existing) {
            return $existing;
        }

        $command = DB::transaction(function () use (
            $device,
            $user,
            $name,
            $validatedPayload,
            $reason,
            $idempotencyKey,
            $definition,
        ): DeviceCommand {
            $now = now();
            $command = DeviceCommand::create([
                'managed_device_id' => $device->id,
                'requested_by' => $user->id,
                'name' => $name,
                'version' => 1,
                'executor' => $definition['executor'] ?? 'service',
                'payload' => $validatedPayload,
                'priority' => ($definition['sensitive'] ?? false) ? 8 : 5,
                'status' => DeviceCommandStatus::Queued,
                'reason' => $reason,
                'sensitive' => (bool) ($definition['sensitive'] ?? false),
                'idempotency_key' => $idempotencyKey,
                'expires_at' => $now->copy()->addSeconds((int) ($definition['ttl_seconds'] ?? 120)),
                'authorized_at' => $now,
                'queued_at' => $now,
            ]);

            foreach ([
                DeviceCommandStatus::Requested,
                DeviceCommandStatus::Authorized,
                DeviceCommandStatus::Queued,
            ] as $status) {
                $this->recordEvent($command, $status->value, 'server', $user);
            }

            return $command;
        });

        $this->audit->record(
            'device.command.requested',
            'queued',
            $user,
            $device,
            reason: $reason,
            metadata: [
                'command_uuid' => $command->uuid,
                'command_name' => $command->name,
            ],
        );

        return $command;
    }

    public function pullNext(ManagedDevice $device, string $executor = 'service'): ?DeviceCommand
    {
        return DB::transaction(function () use ($device, $executor): ?DeviceCommand {
            DeviceCommand::query()
                ->where('managed_device_id', $device->id)
                ->whereIn('status', [
                    DeviceCommandStatus::Queued->value,
                    DeviceCommandStatus::Sent->value,
                ])
                ->where('expires_at', '<=', now())
                ->update([
                    'status' => DeviceCommandStatus::Expired->value,
                    'failed_at' => now(),
                    'error_code' => 'COMMAND_EXPIRED',
                ]);

            $command = DeviceCommand::query()
                ->where('managed_device_id', $device->id)
                ->where('executor', $executor)
                ->whereIn('status', [
                    DeviceCommandStatus::Queued->value,
                    DeviceCommandStatus::Sent->value,
                ])
                ->where('expires_at', '>', now())
                ->orderByDesc('priority')
                ->orderBy('id')
                ->lockForUpdate()
                ->first();

            if (!$command) {
                return null;
            }

            if ($command->status === DeviceCommandStatus::Queued) {
                $command->update([
                    'status' => DeviceCommandStatus::Sent,
                    'sent_at' => now(),
                ]);
                $this->recordEvent($command, DeviceCommandStatus::Sent->value, 'server');
            }

            return $command->fresh();
        });
    }

    public function transitionFromAgent(
        ManagedDevice $device,
        DeviceCommand $command,
        DeviceCommandStatus $target,
        array $result = [],
        ?string $errorCode = null,
        ?string $errorMessage = null,
    ): DeviceCommand {
        if ($command->managed_device_id !== $device->id) {
            throw new AuthorizationException('Command bukan milik perangkat ini.');
        }

        if ($command->status->isTerminal()) {
            return $command;
        }

        $allowed = [
            DeviceCommandStatus::Delivered->value => [
                DeviceCommandStatus::Sent,
            ],
            DeviceCommandStatus::Executing->value => [
                DeviceCommandStatus::Sent,
                DeviceCommandStatus::Delivered,
            ],
            DeviceCommandStatus::Completed->value => [
                DeviceCommandStatus::Sent,
                DeviceCommandStatus::Delivered,
                DeviceCommandStatus::Executing,
            ],
            DeviceCommandStatus::Failed->value => [
                DeviceCommandStatus::Sent,
                DeviceCommandStatus::Delivered,
                DeviceCommandStatus::Executing,
            ],
        ];

        if (!in_array($command->status, $allowed[$target->value] ?? [], true)) {
            throw ValidationException::withMessages([
                'status' => "Transisi {$command->status->value} ke {$target->value} tidak valid.",
            ]);
        }

        $timestampColumn = match ($target) {
            DeviceCommandStatus::Delivered => 'delivered_at',
            DeviceCommandStatus::Executing => 'started_at',
            DeviceCommandStatus::Completed => 'completed_at',
            DeviceCommandStatus::Failed => 'failed_at',
            default => null,
        };

        $updates = [
            'status' => $target,
        ];

        if ($timestampColumn) {
            $updates[$timestampColumn] = now();
        }

        if ($target === DeviceCommandStatus::Completed) {
            $updates['result_summary'] = $this->limitResult($result);
        }

        if ($target === DeviceCommandStatus::Failed) {
            $updates['error_code'] = $errorCode ? mb_substr($errorCode, 0, 80) : 'AGENT_COMMAND_FAILED';
            $updates['error_message'] = $errorMessage ? mb_substr($errorMessage, 0, 2000) : null;
            $updates['result_summary'] = $this->limitResult($result);
        }

        $command->update($updates);
        $this->recordEvent($command, $target->value, 'agent', payload: $this->limitResult($result));

        return $command->fresh();
    }

    public function cancel(DeviceCommand $command, User $user): DeviceCommand
    {
        if ($command->status->isTerminal()) {
            return $command;
        }

        $command->update([
            'status' => DeviceCommandStatus::Cancelled,
            'cancelled_at' => now(),
        ]);
        $this->recordEvent($command, DeviceCommandStatus::Cancelled->value, 'admin', $user);
        $this->audit->record(
            'device.command.cancelled',
            'success',
            $user,
            $command->device,
            metadata: [
                'command_uuid' => $command->uuid,
                'command_name' => $command->name,
            ],
        );

        return $command->fresh();
    }

    private function recordEvent(
        DeviceCommand $command,
        string $event,
        string $source,
        ?User $actor = null,
        array $payload = [],
    ): void {
        $command->events()->create([
            'actor_user_id' => $actor?->id,
            'event' => $event,
            'source' => $source,
            'payload' => $payload,
            'occurred_at' => now(),
        ]);
    }

    private function limitResult(array $result): array
    {
        $json = json_encode($result);

        if ($json !== false && strlen($json) <= 65536) {
            return $result;
        }

        return [
            'truncated' => true,
            'message' => 'Result summary exceeded 64 KiB.',
        ];
    }
}
