@extends('layouts.admin')

@section('admin-content')
<div class="mb-4">
    <h1 class="text-2xl font-bold text-slate-100">Buat Pemberitahuan Baru</h1>
    <p class="text-sm text-slate-400 mt-1">Pemberitahuan ini akan muncul di APK saat diaktifkan</p>
</div>

<div class="max-w-2xl rounded-xl border border-white/10 bg-white/5 backdrop-blur p-6">
    <form action="{{ route('panel.announcements.store') }}" method="POST">
        @csrf
        @include('admin.announcements._form')
    </form>
</div>
@endsection
