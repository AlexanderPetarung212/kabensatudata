@extends('layouts.admin')

@section('admin-content')
<div class="mb-4">
    <h1 class="text-2xl font-bold text-slate-100">Ubah Pemberitahuan</h1>
    <p class="text-sm text-slate-400 mt-1">{{ $announcement->title }}</p>
</div>

<div class="max-w-2xl rounded-xl border border-white/10 bg-white/5 backdrop-blur p-6">
    <form action="{{ route('panel.announcements.update', $announcement) }}" method="POST">
        @csrf @method('PUT')
        @include('admin.announcements._form')
    </form>
</div>
@endsection
