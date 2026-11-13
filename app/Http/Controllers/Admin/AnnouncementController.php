<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Announcement;
use Illuminate\Http\Request;

class AnnouncementController extends Controller
{
    public function index()
    {
        $announcements = Announcement::orderByDesc('is_active')
            ->orderByDesc('updated_at')
            ->paginate(20);

        return view('admin.announcements.index', compact('announcements'));
    }

    public function create()
    {
        return view('admin.announcements.create');
    }

    public function store(Request $request)
    {
        $validated = $request->validate([
            'title'   => 'required|string|max:255',
            'message' => 'required|string|max:2000',
            'type'    => 'required|in:info,warning,update,critical',
        ]);

        Announcement::create($validated);

        return redirect()->route('panel.announcements.index')
            ->with('success', 'Pemberitahuan berhasil dibuat.');
    }

    public function edit(Announcement $announcement)
    {
        return view('admin.announcements.edit', compact('announcement'));
    }

    public function update(Request $request, Announcement $announcement)
    {
        $validated = $request->validate([
            'title'   => 'required|string|max:255',
            'message' => 'required|string|max:2000',
            'type'    => 'required|in:info,warning,update,critical',
        ]);

        $announcement->update($validated);

        return redirect()->route('panel.announcements.index')
            ->with('success', 'Pemberitahuan berhasil diperbarui.');
    }

    public function destroy(Announcement $announcement)
    {
        $announcement->delete();

        return redirect()->route('panel.announcements.index')
            ->with('success', 'Pemberitahuan berhasil dihapus.');
    }

    /**
     * Toggle active status.
     * If activating, deactivate all others first.
     */
    public function toggle(Announcement $announcement)
    {
        if ($announcement->is_active) {
            $announcement->deactivate();
            $message = 'Pemberitahuan dinonaktifkan.';
        } else {
            $announcement->activate();
            $message = 'Pemberitahuan diaktifkan. Pemberitahuan lain otomatis dinonaktifkan.';
        }

        return redirect()->route('panel.announcements.index')
            ->with('success', $message);
    }
}
