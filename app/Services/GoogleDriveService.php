<?php

namespace App\Services;

use Google\Client as GoogleClient;
use Google\Service\Drive as GoogleDrive;
use Google\Service\Drive\DriveFile;
use Google\Service\Drive\Permission;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Log;

class GoogleDriveService
{
    private GoogleClient $client;
    private GoogleDrive $drive;
    private ?string $rootFolderId;

    public function __construct()
    {
        $this->client = new GoogleClient();
        $this->client->setClientId(config('services.google_drive.client_id'));
        $this->client->setClientSecret(config('services.google_drive.client_secret'));
        $this->client->addScope(GoogleDrive::DRIVE);
        $this->client->setAccessType('offline');

        $this->client->fetchAccessTokenWithRefreshToken(
            config('services.google_drive.refresh_token')
        );

        $this->drive = new GoogleDrive($this->client);
        $this->rootFolderId = config('services.google_drive.folder_id');
    }

    /**
     * Upload file to Google Drive
     *
     * @param UploadedFile|string $file  UploadedFile or local file path
     * @param string $folderName         Subfolder name (e.g. "Pegawai_NIP_123")
     * @param string|null $fileName      Custom filename
     * @return array{file_id: string, web_view_link: string, direct_link: string}
     */
    public function upload($file, string $folderName, ?string $fileName = null): array
    {
        try {
            // Get or create subfolder
            $folderId = $this->getOrCreateFolder($folderName, $this->rootFolderId);

            // Prepare file metadata
            $fileMetadata = new DriveFile();

            if ($file instanceof UploadedFile) {
                $fileMetadata->setName($fileName ?? $file->getClientOriginalName());
                $content = file_get_contents($file->getRealPath());
                $mimeType = $file->getMimeType();
            } else {
                // Local file path
                $fileMetadata->setName($fileName ?? basename($file));
                $content = file_get_contents($file);
                $mimeType = mime_content_type($file) ?: 'application/octet-stream';
            }

            $fileMetadata->setParents([$folderId]);

            // Upload
            $uploadedFile = $this->drive->files->create($fileMetadata, [
                'data' => $content,
                'mimeType' => $mimeType,
                'uploadType' => 'multipart',
                'fields' => 'id, webViewLink, webContentLink',
            ]);

            // Set file permission to "anyone with link can view"
            $permission = new Permission();
            $permission->setType('anyone');
            $permission->setRole('reader');
            $this->drive->permissions->create($uploadedFile->getId(), $permission);

            // Get direct download link
            $fileId = $uploadedFile->getId();
            $directLink = $uploadedFile->getWebContentLink();

            return [
                'file_id' => $fileId,
                'web_view_link' => $uploadedFile->getWebViewLink(),
                'direct_link' => $directLink,
            ];
        } catch (\Exception $e) {
            Log::error('Google Drive upload error: ' . $e->getMessage());
            throw $e;
        }
    }

    /**
     * Delete file from Google Drive
     */
    public function delete(string $fileId): bool
    {
        try {
            $this->drive->files->delete($fileId);
            return true;
        } catch (\Exception $e) {
            Log::error("Google Drive delete error (ID: {$fileId}): " . $e->getMessage());
            return false;
        }
    }

    /**
     * Get direct download link for a file
     */
    public function getDirectLink(string $fileId): string
    {
        return "https://drive.google.com/uc?export=download&id={$fileId}";
    }

    /**
     * Get thumbnail link for preview (images)
     */
    public function getThumbnailLink(string $fileId, int $size = 600): string
    {
        return "https://drive.google.com/thumbnail?id={$fileId}&sz=s{$size}";
    }

    /**
     * Get or create a folder in Google Drive
     */
    private function getOrCreateFolder(string $folderName, ?string $parentId = null): string
    {
        // Search for existing folder
        $query = "name='{$folderName}' and mimeType='application/vnd.google-apps.folder' and trashed=false";
        if ($parentId) {
            $query .= " and '{$parentId}' in parents";
        }

        $results = $this->drive->files->listFiles([
            'q' => $query,
            'fields' => 'files(id)',
            'spaces' => 'drive',
        ]);

        if (count($results->getFiles()) > 0) {
            return $results->getFiles()[0]->getId();
        }

        // Create folder
        $folderMetadata = new DriveFile();
        $folderMetadata->setName($folderName);
        $folderMetadata->setMimeType('application/vnd.google-apps.folder');
        if ($parentId) {
            $folderMetadata->setParents([$parentId]);
        }

        $folder = $this->drive->files->create($folderMetadata, [
            'fields' => 'id',
        ]);

        return $folder->getId();
    }
}
