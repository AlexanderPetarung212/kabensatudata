<?php

namespace App\Console\Commands;

use Illuminate\Console\Command;
use Kreait\Firebase\Messaging\CloudMessage;
use Kreait\Firebase\Messaging\Notification;

class SendPushNotification extends Command
{
    protected $signature = 'notify:send 
        {title : Judul notifikasi} 
        {body : Isi pesan} 
        {--type=info : Tipe: info, warning, update, critical}';

    protected $description = 'Kirim push notification ke semua user APK';

    public function handle()
    {
        $messaging = app('firebase.messaging');

        $message = \Kreait\Firebase\Messaging\CloudMessage::fromArray([
            'topic' => 'kabensatudata_all',
            'notification' => [
                'title' => $this->argument('title'),
                'body'  => $this->argument('body'),
            ],
            'data' => [
                'type' => $this->option('type'),
                'id'   => (string) now()->timestamp,
            ],
        ]);

        $messaging->send($message);

        $this->info('✅ Notifikasi berhasil dikirim!');
    }
}
