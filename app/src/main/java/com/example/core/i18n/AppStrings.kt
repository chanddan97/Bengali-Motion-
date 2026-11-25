package com.example.core.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.core.model.AppLanguage
import com.example.core.model.AspectRatio
import com.example.core.model.ProjectWorkflowStep

data class StudioStrings(
    val appTitle: String,
    val appSubtitle: String,
    val offlineReadyBadge: String,
    val navProjects: String,
    val navTemplates: String,
    val navSettings: String,
    val newProjectCta: String,
    val createFirstProjectCta: String,
    val searchProjectsHint: String,
    val filterActive: String,
    val filterArchived: String,
    val activeCountLabel: (Int) -> String,
    val archivedCountLabel: (Int) -> String,
    val emptyProjectsTitle: String,
    val emptyProjectsBody: String,
    val emptyArchivedTitle: String,
    val emptyArchivedBody: String,
    val emptySearchTitle: String,
    val emptySearchBody: String,
    val createProjectTitle: String,
    val projectNameLabel: String,
    val projectNamePlaceholder: String,
    val projectNameRequiredError: String,
    val projectNameTooLongError: String,
    val aspectRatioLabel: String,
    val projectLanguageLabel: String,
    val resolutionLabel: String,
    val createAction: String,
    val cancelAction: String,
    val saveAction: String,
    val renameProjectTitle: String,
    val deleteProjectTitle: String,
    val deleteProjectMessage: (String) -> String,
    val deleteAction: String,
    val actionOpen: String,
    val actionRename: String,
    val actionDuplicate: String,
    val actionArchive: String,
    val actionRestore: String,
    val actionDelete: String,
    val duplicateSuffix: String,
    val autosavedBadge: String,
    val projectCreatedMessage: (String) -> String,
    val projectRenamedMessage: String,
    val projectDuplicatedMessage: String,
    val projectArchivedMessage: String,
    val projectRestoredMessage: String,
    val projectDeletedMessage: String,
    val formatUpdatedMessage: String,
    val projectOverviewTitle: String,
    val projectNotFoundTitle: String,
    val projectNotFoundBody: String,
    val backToProjectsAction: String,
    val formatAndLanguageSectionTitle: String,
    val formatAndLanguageSectionHint: String,
    val workflowRoadmapTitle: String,
    val workflowRoadmapHint: String,
    val activeNowBadge: String,
    val milestoneBadge: (Int) -> String,
    val watermarkStatusSummary: String,
    val templatesScreenTitle: String,
    val templatesMilestoneBannerTitle: String,
    val templatesMilestoneBannerBody: String,
    val observedGroupTitle: String,
    val observedGroupSubtitle: String,
    val proposedGroupTitle: String,
    val proposedGroupSubtitle: String,
    val settingsScreenTitle: String,
    val uiLanguageSectionTitle: String,
    val uiLanguageSectionHint: String,
    val defaultProjectFormatTitle: String,
    val defaultProjectFormatHint: String,
    val localStorageSectionTitle: String,
    val localStoragePathLabel: String,
    val localProjectsSummaryLabel: String,
    val localDiskUsedLabel: String,
    val localDiskFreeLabel: String,
    val privacyGuaranteeTitle: String,
    val privacyGuaranteeBody: String,
    val milestoneRoadmapCardTitle: String,
    val milestone1Summary: String,
    val updatedPrefix: String,
    val fpsSuffix: String,
    val stepTitle: (ProjectWorkflowStep) -> String,
    val stepDescription: (ProjectWorkflowStep) -> String,
    val aspectRatioHint: (AspectRatio) -> String,
)

val LocalStudioStrings = staticCompositionLocalOf { stringsFor(AppLanguage.BENGALI_ENGLISH) }

fun stringsFor(language: AppLanguage): StudioStrings = when (language) {
    AppLanguage.BENGALI -> bengaliStrings()
    AppLanguage.ENGLISH -> englishStrings()
    AppLanguage.BENGALI_ENGLISH -> bilingualStrings()
}

private fun bengaliStrings() = StudioStrings(
    appTitle = "বাংলা-মোশন স্টুডিও",
    appSubtitle = "অফলাইন মোবাইল মোশন স্টুডিও",
    offlineReadyBadge = "অফলাইন প্রস্তুত",
    navProjects = "প্রোজেক্ট",
    navTemplates = "টেমপ্লেট",
    navSettings = "সেটিংস",
    newProjectCta = "নতুন প্রোজেক্ট",
    createFirstProjectCta = "প্রথম প্রোজেক্ট তৈরি করুন",
    searchProjectsHint = "প্রোজেক্ট খুঁজুন...",
    filterActive = "সক্রিয়",
    filterArchived = "আর্কাইভ",
    activeCountLabel = { "সক্রিয় ($it)" },
    archivedCountLabel = { "আর্কাইভ ($it)" },
    emptyProjectsTitle = "এখনো কোনো প্রোজেক্ট নেই",
    emptyProjectsBody = "আপনার প্রথম বাংলা বা ইংরেজি মোশন ভিডিও প্রোজেক্ট তৈরি করুন। সব ডেটা ফোনেই লোকালভাবে সংরক্ষিত থাকবে।",
    emptyArchivedTitle = "আর্কাইভ খালি",
    emptyArchivedBody = "আর্কাইভ করা প্রোজেক্টগুলো এখানে থাকবে এবং যেকোনো সময় পুনরুদ্ধার করা যাবে।",
    emptySearchTitle = "কোনো প্রোজেক্ট পাওয়া যায়নি",
    emptySearchBody = "অন্য নাম দিয়ে চেষ্টা করুন অথবা সার্চ ফিল্টার মুছে দিন।",
    createProjectTitle = "নতুন প্রোজেক্ট তৈরি করুন",
    projectNameLabel = "প্রোজেক্টের নাম",
    projectNamePlaceholder = "যেমন: সকালের গল্প, রিলস এপিসোড ১",
    projectNameRequiredError = "প্রোজেক্টের নাম লিখুন।",
    projectNameTooLongError = "সর্বোচ্চ ৮০ অক্ষরের মধ্যে নাম লিখুন।",
    aspectRatioLabel = "ভিডিও অনুপাত (Aspect Ratio)",
    projectLanguageLabel = "প্রোজেক্টের ভাষা",
    resolutionLabel = "এক্সপোর্ট রেজোলিউশন",
    createAction = "তৈরি করুন",
    cancelAction = "বাতিল",
    saveAction = "সংরক্ষণ",
    renameProjectTitle = "প্রোজেক্টের নাম পরিবর্তন",
    deleteProjectTitle = "প্রোজেক্ট মুছে ফেলবেন?",
    deleteProjectMessage = { "\"$it\" এবং এর লোকাল প্রোজেক্ট ফোল্ডার স্থায়ীভাবে মুছে যাবে।" },
    deleteAction = "মুছে ফেলুন",
    actionOpen = "খুলুন",
    actionRename = "নাম পরিবর্তন",
    actionDuplicate = "কপি তৈরি করুন",
    actionArchive = "আর্কাইভ করুন",
    actionRestore = "পুনরুদ্ধার করুন",
    actionDelete = "মুছে ফেলুন",
    duplicateSuffix = " (কপি)",
    autosavedBadge = "অটো-সেভড (লোকাল)",
    projectCreatedMessage = { "\"$it\" তৈরি হয়েছে" },
    projectRenamedMessage = "প্রোজেক্টের নাম আপডেট হয়েছে",
    projectDuplicatedMessage = "প্রোজেক্টের কপি তৈরি হয়েছে",
    projectArchivedMessage = "প্রোজেক্ট আর্কাইভে সরানো হয়েছে",
    projectRestoredMessage = "প্রোজেক্ট পুনরুদ্ধার করা হয়েছে",
    projectDeletedMessage = "প্রোজেক্ট স্থায়ীভাবে মুছে ফেলা হয়েছে",
    formatUpdatedMessage = "ফরম্যাট ও ভাষা লোকালভাবে সংরক্ষিত হয়েছে",
    projectOverviewTitle = "প্রোজেক্ট ওয়ার্কস্পেস",
    projectNotFoundTitle = "প্রোজেক্ট পাওয়া যায়নি",
    projectNotFoundBody = "প্রোজেক্টটি হয়তো মুছে ফেলা হয়েছে।",
    backToProjectsAction = "প্রোজেক্ট তালিকায় ফিরুন",
    formatAndLanguageSectionTitle = "ধাপ ৪ · ফরম্যাট ও ভাষা (সক্রিয়)",
    formatAndLanguageSectionHint = "যেকোনো পরিবর্তন সাথে সাথে লোকাল Room ডাটাবেসে সংরক্ষিত হয়।",
    workflowRoadmapTitle = "১০-ধাপের ভিডিও ওয়ার্কফ্লো",
    workflowRoadmapHint = "মাইলস্টোন ১-এ প্রোজেক্ট পরিচালনা ও ফরম্যাট/ভাষা সক্রিয়। বাকি ধাপগুলো পরবর্তী মাইলস্টোনে যুক্ত হবে।",
    activeNowBadge = "সক্রিয়",
    milestoneBadge = { "মাইলস্টোন $it" },
    watermarkStatusSummary = "ইউজার ওয়াটারমার্ক: বন্ধ · ডেভেলপার ওয়াটারমার্ক: বন্ধ (ডিফল্ট)",
    templatesScreenTitle = "টেমপ্লেট গ্যালারি",
    templatesMilestoneBannerTitle = "মাইলস্টোন ৩-এ আসছে",
    templatesMilestoneBannerBody = "সরবরাহকৃত JSON ক্যাটালগ থেকে মোট ২০টি টেমপ্লেট (১০টি পর্যবেক্ষিত লেবেল + ১০টি নতুন প্রস্তাবিত ডিজাইন) স্কিমা যাচাইসহ লোড হবে।",
    observedGroupTitle = "১০টি পর্যবেক্ষিত লেবেল (মৌলিক ডিজাইন)",
    observedGroupSubtitle = "রেফারেন্স ভিডিওতে দৃশ্যমান নাম — প্রতিটি নিজস্ব মৌলিক লেআউটে তৈরি হবে।",
    proposedGroupTitle = "১০টি প্রস্তাবিত মৌলিক টেমপ্লেট",
    proposedGroupSubtitle = "অতিরিক্ত নতুন ডিজাইন, পর্যবেক্ষিত লেবেল থেকে আলাদাভাবে চিহ্নিত।",
    settingsScreenTitle = "স্টুডিও সেটিংস",
    uiLanguageSectionTitle = "অ্যাপের ভাষা",
    uiLanguageSectionHint = "বাংলা, English অথবা দ্বিভাষিক (বাংলা + English) বেছে নিন।",
    defaultProjectFormatTitle = "নতুন প্রোজেক্টের ডিফল্ট সেটিংস",
    defaultProjectFormatHint = "নতুন প্রোজেক্ট তৈরির সময় এই অনুপাত ও রেজোলিউশন আগে থেকে নির্বাচিত থাকবে।",
    localStorageSectionTitle = "লোকাল স্টোরেজ ও অফলাইন স্ট্যাটাস",
    localStoragePathLabel = "প্রোজেক্ট ফোল্ডার",
    localProjectsSummaryLabel = "সংরক্ষিত প্রোজেক্ট",
    localDiskUsedLabel = "অ্যাপের ব্যবহৃত জায়গা",
    localDiskFreeLabel = "ফোনে খালি জায়গা",
    privacyGuaranteeTitle = "প্রাইভেসি ও নিরাপত্তা নীতি",
    privacyGuaranteeBody = "কোনো API কী APK-তে নেই। মূল এডিটিং অফলাইনে কাজ করে। কোনো বাধ্যতামূলক সাবস্ক্রিপশন বা জোরপূর্বক ওয়াটারমার্ক নেই।",
    milestoneRoadmapCardTitle = "বর্তমান সংস্করণ · মাইলস্টোন ১",
    milestone1Summary = "প্রোজেক্ট CRUD, লোকাল Room + DataStore সংরক্ষণ, বাংলা/ইংরেজি ভাষা পরিবর্তন এবং রেসপনসিভ স্টুডিও UI সক্রিয়। পরবর্তী: মাইলস্টোন ২ (স্ক্রিপ্ট ও UTF-8 SRT)।",
    updatedPrefix = "আপডেট:",
    fpsSuffix = "fps",
    stepTitle = { step ->
        when (step) {
            ProjectWorkflowStep.SCRIPT -> "১. স্ক্রিপ্ট"
            ProjectWorkflowStep.VOICE_AUDIO -> "২. ভয়েস / অডিও"
            ProjectWorkflowStep.SRT -> "৩. SRT সাবটাইটেল"
            ProjectWorkflowStep.FORMAT_LANGUAGE -> "৪. ফরম্যাট ও ভাষা"
            ProjectWorkflowStep.TEMPLATE -> "৫. টেমপ্লেট"
            ProjectWorkflowStep.AUTO_MATCH -> "৬. অটো ম্যাচ"
            ProjectWorkflowStep.SCENES -> "৭. সিন এডিটর"
            ProjectWorkflowStep.PREVIEW -> "৮. প্রিভিউ"
            ProjectWorkflowStep.WATERMARK -> "৯. ওয়াটারমার্ক"
            ProjectWorkflowStep.EXPORT -> "১০. MP4 এক্সপোর্ট"
        }
    },
    stepDescription = { step ->
        when (step) {
            ProjectWorkflowStep.SCRIPT -> "বাংলা ও ইংরেজি স্ক্রিপ্ট এডিটর"
            ProjectWorkflowStep.VOICE_AUDIO -> "অডিও ইমপোর্ট, প্লেব্যাক ও মাইক্রোফোন রেকর্ডিং"
            ProjectWorkflowStep.SRT -> "অফলাইন UTF-8 SRT ইমপোর্ট, এডিট ও এক্সপোর্ট"
            ProjectWorkflowStep.FORMAT_LANGUAGE -> "অনুপাত, রেজোলিউশন ও ভাষা নির্বাচন"
            ProjectWorkflowStep.TEMPLATE -> "২০টি JSON-চালিত ভিজ্যুয়াল টেমপ্লেট"
            ProjectWorkflowStep.AUTO_MATCH -> "SRT কিউ থেকে সিন স্বয়ংক্রিয় বিভাজন"
            ProjectWorkflowStep.SCENES -> "লেয়ার, টেক্সট, ব্যাকগ্রাউন্ড ও মোশন প্রিসেট"
            ProjectWorkflowStep.PREVIEW -> "রিয়েল-টাইম ডিটারমিনিস্টিক ক্যানভাস প্রিভিউ"
            ProjectWorkflowStep.WATERMARK -> "ইউজার ও ডেভেলপার ওয়াটারমার্ক (উভয়ই ডিফল্টে বন্ধ)"
            ProjectWorkflowStep.EXPORT -> "ডিভাইস এনকোডার দিয়ে আসল H.264/AAC MP4 রেন্ডার"
        }
    },
    aspectRatioHint = { it.orientationTagBn },
)

private fun englishStrings() = StudioStrings(
    appTitle = "BanglaMotion Studio",
    appSubtitle = "Offline Mobile Motion Studio",
    offlineReadyBadge = "Offline Ready",
    navProjects = "Projects",
    navTemplates = "Templates",
    navSettings = "Settings",
    newProjectCta = "New Project",
    createFirstProjectCta = "Create First Project",
    searchProjectsHint = "Search projects...",
    filterActive = "Active",
    filterArchived = "Archived",
    activeCountLabel = { "Active ($it)" },
    archivedCountLabel = { "Archived ($it)" },
    emptyProjectsTitle = "No projects yet",
    emptyProjectsBody = "Create your first Bengali or English motion project. Everything saves locally on your phone with zero internet required.",
    emptyArchivedTitle = "Archive is empty",
    emptyArchivedBody = "Archived projects stay stored locally and can be restored anytime.",
    emptySearchTitle = "No matching projects",
    emptySearchBody = "Try another project name or clear your search query.",
    createProjectTitle = "Create New Project",
    projectNameLabel = "Project Name",
    projectNamePlaceholder = "e.g., Morning Story, Reels Ep 1",
    projectNameRequiredError = "Please enter a project name.",
    projectNameTooLongError = "Keep the name within 80 characters.",
    aspectRatioLabel = "Aspect Ratio",
    projectLanguageLabel = "Project Language",
    resolutionLabel = "Export Resolution",
    createAction = "Create",
    cancelAction = "Cancel",
    saveAction = "Save",
    renameProjectTitle = "Rename Project",
    deleteProjectTitle = "Delete Project?",
    deleteProjectMessage = { "This will permanently delete \"$it\" and its local project folder." },
    deleteAction = "Delete",
    actionOpen = "Open",
    actionRename = "Rename",
    actionDuplicate = "Duplicate",
    actionArchive = "Archive",
    actionRestore = "Restore",
    actionDelete = "Delete",
    duplicateSuffix = " (Copy)",
    autosavedBadge = "Autosaved locally",
    projectCreatedMessage = { "Created \"$it\"" },
    projectRenamedMessage = "Project renamed",
    projectDuplicatedMessage = "Project duplicated",
    projectArchivedMessage = "Project moved to archive",
    projectRestoredMessage = "Project restored",
    projectDeletedMessage = "Project permanently deleted",
    formatUpdatedMessage = "Format & language saved locally",
    projectOverviewTitle = "Project Workspace",
    projectNotFoundTitle = "Project not found",
    projectNotFoundBody = "This project may have been deleted.",
    backToProjectsAction = "Back to Projects",
    formatAndLanguageSectionTitle = "Step 4 · Format & Language (Active)",
    formatAndLanguageSectionHint = "Changes autosave immediately to the local Room database.",
    workflowRoadmapTitle = "10-Step Video Workflow",
    workflowRoadmapHint = "Milestone 1 enables project management and Format/Language. Remaining steps unlock in upcoming milestones.",
    activeNowBadge = "Active",
    milestoneBadge = { "Milestone $it" },
    watermarkStatusSummary = "User Watermark: OFF · Developer Watermark: OFF (Default)",
    templatesScreenTitle = "Template Gallery",
    templatesMilestoneBannerTitle = "Scheduled for Milestone 3",
    templatesMilestoneBannerBody = "All 20 templates (10 reference-observed labels + 10 proposed original designs) will load from the validated JSON catalogue in Milestone 3.",
    observedGroupTitle = "10 Reference-Observed Labels (Original Designs)",
    observedGroupSubtitle = "Labels observed in sampled footage, each implemented with original artwork.",
    proposedGroupTitle = "10 Proposed Original Templates",
    proposedGroupSubtitle = "Additional original styles kept clearly separated from observed labels.",
    settingsScreenTitle = "Studio Settings",
    uiLanguageSectionTitle = "Interface Language",
    uiLanguageSectionHint = "Switch instantaneously between বাংলা, English, or বাংলা + English.",
    defaultProjectFormatTitle = "Default New Project Format",
    defaultProjectFormatHint = "Pre-selected when you create a new project.",
    localStorageSectionTitle = "Local Storage & Offline Status",
    localStoragePathLabel = "Managed project directory",
    localProjectsSummaryLabel = "Stored projects",
    localDiskUsedLabel = "App project storage used",
    localDiskFreeLabel = "Device free space",
    privacyGuaranteeTitle = "Privacy & Security Guarantees",
    privacyGuaranteeBody = "No API keys embedded in APK. Core workflow runs offline. No compulsory subscriptions or forced watermarks.",
    milestoneRoadmapCardTitle = "Current Build · Milestone 1",
    milestone1Summary = "Project CRUD, Room + DataStore local persistence, Bengali/English switcher, and responsive mobile UI are active. Next up: Milestone 2 (Script & UTF-8 SRT).",
    updatedPrefix = "Updated:",
    fpsSuffix = "fps",
    stepTitle = { step ->
        when (step) {
            ProjectWorkflowStep.SCRIPT -> "1. Script"
            ProjectWorkflowStep.VOICE_AUDIO -> "2. Voice / Audio"
            ProjectWorkflowStep.SRT -> "3. SRT Subtitles"
            ProjectWorkflowStep.FORMAT_LANGUAGE -> "4. Format & Language"
            ProjectWorkflowStep.TEMPLATE -> "5. Template"
            ProjectWorkflowStep.AUTO_MATCH -> "6. Auto Match"
            ProjectWorkflowStep.SCENES -> "7. Scene Editor"
            ProjectWorkflowStep.PREVIEW -> "8. Preview"
            ProjectWorkflowStep.WATERMARK -> "9. Watermark"
            ProjectWorkflowStep.EXPORT -> "10. Export MP4"
        }
    },
    stepDescription = { step ->
        when (step) {
            ProjectWorkflowStep.SCRIPT -> "Bengali & English script authoring"
            ProjectWorkflowStep.VOICE_AUDIO -> "Audio import, playback & runtime-permission recording"
            ProjectWorkflowStep.SRT -> "Offline UTF-8 SRT import, editor, sync & export"
            ProjectWorkflowStep.FORMAT_LANGUAGE -> "Aspect ratio, export resolution & project language"
            ProjectWorkflowStep.TEMPLATE -> "20 JSON-driven visual templates"
            ProjectWorkflowStep.AUTO_MATCH -> "Split scenes from SRT cues automatically"
            ProjectWorkflowStep.SCENES -> "Layers, text, backgrounds & motion presets"
            ProjectWorkflowStep.PREVIEW -> "Deterministic canvas preview matching export"
            ProjectWorkflowStep.WATERMARK -> "User & optional developer watermark (both OFF by default)"
            ProjectWorkflowStep.EXPORT -> "Real H.264/AAC MP4 encoding on device"
        }
    },
    aspectRatioHint = { it.orientationTagEn },
)

private fun bilingualStrings() = StudioStrings(
    appTitle = "BanglaMotion · বাংলা-মোশন",
    appSubtitle = "অফলাইন মোবাইল স্টুডিও · Offline Mobile Studio",
    offlineReadyBadge = "অফলাইন · Offline",
    navProjects = "প্রোজেক্ট / Projects",
    navTemplates = "টেমপ্লেট / Templates",
    navSettings = "সেটিংস / Settings",
    newProjectCta = "নতুন প্রোজেক্ট · New",
    createFirstProjectCta = "প্রথম প্রোজেক্ট তৈরি · Create Project",
    searchProjectsHint = "প্রোজেক্ট খুঁজুন · Search projects...",
    filterActive = "সক্রিয় · Active",
    filterArchived = "আর্কাইভ · Archived",
    activeCountLabel = { "সক্রিয় / Active ($it)" },
    archivedCountLabel = { "আর্কাইভ / Archived ($it)" },
    emptyProjectsTitle = "কোনো প্রোজেক্ট নেই · No Projects Yet",
    emptyProjectsBody = "প্রথম বাংলা বা ইংরেজি মোশন প্রোজেক্ট তৈরি করুন। সব ডেটা ফোনেই অফলাইনে সংরক্ষিত থাকবে।",
    emptyArchivedTitle = "আর্কাইভ খালি · Archive Empty",
    emptyArchivedBody = "আর্কাইভ করা প্রোজেক্ট এখানে সংরক্ষিত থাকে এবং যেকোনো সময় ফিরিয়ে আনা যায়।",
    emptySearchTitle = "কিছু পাওয়া যায়নি · No Matches",
    emptySearchBody = "অন্য নাম লিখুন অথবা সার্চ ক্লিয়ার করুন।",
    createProjectTitle = "নতুন প্রোজেক্ট · New Project",
    projectNameLabel = "প্রোজেক্টের নাম · Project Name",
    projectNamePlaceholder = "যেমন: আজকের গল্প / Reels Ep 1",
    projectNameRequiredError = "প্রোজেক্টের নাম দিন · Project name is required.",
    projectNameTooLongError = "সর্বোচ্চ ৮০ অক্ষর · Max 80 characters.",
    aspectRatioLabel = "অনুপাত · Aspect Ratio",
    projectLanguageLabel = "প্রোজেক্টের ভাষা · Project Language",
    resolutionLabel = "রেজোলিউশন · Export Resolution",
    createAction = "তৈরি করুন · Create",
    cancelAction = "বাতিল · Cancel",
    saveAction = "সংরক্ষণ · Save",
    renameProjectTitle = "নাম পরিবর্তন · Rename Project",
    deleteProjectTitle = "প্রোজেক্ট মুছবেন? · Delete Project?",
    deleteProjectMessage = { "\"$it\" এবং এর লোকাল ফোল্ডার স্থায়ীভাবে মুছে যাবে / Permanently deleted." },
    deleteAction = "মুছুন · Delete",
    actionOpen = "খুলুন · Open",
    actionRename = "নাম পরিবর্তন · Rename",
    actionDuplicate = "কপি · Duplicate",
    actionArchive = "আর্কাইভ · Archive",
    actionRestore = "পুনরুদ্ধার · Restore",
    actionDelete = "মুছুন · Delete",
    duplicateSuffix = " (Copy)",
    autosavedBadge = "অটো-সেভড · Autosaved",
    projectCreatedMessage = { "তৈরি হয়েছে · Created \"$it\"" },
    projectRenamedMessage = "নাম আপডেট হয়েছে · Renamed",
    projectDuplicatedMessage = "কপি তৈরি হয়েছে · Duplicated",
    projectArchivedMessage = "আর্কাইভ করা হয়েছে · Archived",
    projectRestoredMessage = "পুনরুদ্ধার হয়েছে · Restored",
    projectDeletedMessage = "মুছে ফেলা হয়েছে · Deleted",
    formatUpdatedMessage = "ফরম্যাট সংরক্ষিত · Format saved locally",
    projectOverviewTitle = "ওয়ার্কস্পেস · Workspace",
    projectNotFoundTitle = "প্রোজেক্ট পাওয়া যায়নি · Not Found",
    projectNotFoundBody = "প্রোজেক্টটি মুছে ফেলা হয়ে থাকতে পারে।",
    backToProjectsAction = "প্রোজেক্ট তালিকায় ফিরুন · Back",
    formatAndLanguageSectionTitle = "ধাপ ৪ · ফরম্যাট ও ভাষা / Format & Language",
    formatAndLanguageSectionHint = "পরিবর্তন সাথে সাথে লোকাল Room ডাটাবেসে সেভ হয় / Autosaves locally.",
    workflowRoadmapTitle = "১০-ধাপের স্টুডিও ফ্লো · 10-Step Workflow",
    workflowRoadmapHint = "মাইলস্টোন ১-এ প্রোজেক্ট ও ফরম্যাট/ভাষা সক্রিয়। বাকি ধাপগুলো ধাপে ধাপে যুক্ত হবে।",
    activeNowBadge = "সক্রিয় · Live",
    milestoneBadge = { "M$it" },
    watermarkStatusSummary = "User Watermark: OFF · Developer Watermark: OFF (ডিফল্ট বন্ধ)",
    templatesScreenTitle = "টেমপ্লেট · Templates",
    templatesMilestoneBannerTitle = "মাইলস্টোন ৩-এ যুক্ত হবে · Coming in Milestone 3",
    templatesMilestoneBannerBody = "সরবরাহকৃত JSON স্কিমা অনুযায়ী ২০টি টেমপ্লেট (১০টি রেফারেন্স-পর্যবেক্ষিত লেবেল + ১০টি নতুন প্রস্তাবিত ডিজাইন) মাইলস্টোন ৩-এ লোড হবে।",
    observedGroupTitle = "১০টি পর্যবেক্ষিত লেবেল · 10 Observed Labels",
    observedGroupSubtitle = "রেফারেন্স ভিডিওতে দৃশ্যমান নাম, নিজস্ব মৌলিক ডিজাইনে।",
    proposedGroupTitle = "১০টি প্রস্তাবিত ডিজাইন · 10 Proposed Originals",
    proposedGroupSubtitle = "আলাদাভাবে চিহ্নিত অতিরিক্ত মৌলিক টেমপ্লেট।",
    settingsScreenTitle = "সেটিংস · Settings",
    uiLanguageSectionTitle = "অ্যাপের ভাষা · App Language",
    uiLanguageSectionHint = "বাংলা, English বা দ্বিভাষিক মোড বেছে নিন।",
    defaultProjectFormatTitle = "ডিফল্ট ফরম্যাট · Default Format",
    defaultProjectFormatHint = "নতুন প্রোজেক্ট তৈরিতে আগে থেকেই নির্বাচিত থাকবে।",
    localStorageSectionTitle = "লোকাল স্টোরেজ · Local Storage",
    localStoragePathLabel = "ফোল্ডার · Folder",
    localProjectsSummaryLabel = "সংরক্ষিত প্রোজেক্ট · Projects",
    localDiskUsedLabel = "ব্যবহৃত · Used",
    localDiskFreeLabel = "খালি জায়গা · Free",
    privacyGuaranteeTitle = "প্রাইভেসি ও নিরাপত্তা · Privacy & Security",
    privacyGuaranteeBody = "কোনো API কী APK-তে নেই · অফলাইন-ফার্স্ট · বাধ্যতামূলক ওয়াটারমার্ক বা সাবস্ক্রিপশন নেই।",
    milestoneRoadmapCardTitle = "বর্তমান ধাপ · Milestone 1",
    milestone1Summary = "Project CRUD, Room + DataStore লোকাল স্টোরেজ, বাংলা/English ভাষা ও রেসপনসিভ UI সক্রিয়। পরবর্তী: Milestone 2 (Script & UTF-8 SRT)।",
    updatedPrefix = "আপডেট · Updated:",
    fpsSuffix = "fps",
    stepTitle = { step ->
        when (step) {
            ProjectWorkflowStep.SCRIPT -> "১. স্ক্রিপ্ট · Script"
            ProjectWorkflowStep.VOICE_AUDIO -> "২. ভয়েস · Voice / Audio"
            ProjectWorkflowStep.SRT -> "৩. SRT সাবটাইটেল"
            ProjectWorkflowStep.FORMAT_LANGUAGE -> "৪. ফরম্যাট ও ভাষা · Format"
            ProjectWorkflowStep.TEMPLATE -> "৫. টেমপ্লেট · Template"
            ProjectWorkflowStep.AUTO_MATCH -> "৬. অটো ম্যাচ · Auto Match"
            ProjectWorkflowStep.SCENES -> "৭. সিন এডিটর · Scenes"
            ProjectWorkflowStep.PREVIEW -> "৮. প্রিভিউ · Preview"
            ProjectWorkflowStep.WATERMARK -> "৯. ওয়াটারমার্ক · Watermark"
            ProjectWorkflowStep.EXPORT -> "১০. এক্সপোর্ট · Export MP4"
        }
    },
    stepDescription = { step ->
        when (step) {
            ProjectWorkflowStep.SCRIPT -> "বাংলা ও ইংরেজি স্ক্রিপ্ট এডিটর · Script editor"
            ProjectWorkflowStep.VOICE_AUDIO -> "অডিও ইমপোর্ট ও রেকর্ডিং · Audio import & record"
            ProjectWorkflowStep.SRT -> "অফলাইন UTF-8 SRT এডিটর ও এক্সপোর্ট"
            ProjectWorkflowStep.FORMAT_LANGUAGE -> "9:16, 1:1, 4:5, 16:9 · 720p/1080p · ভাষা"
            ProjectWorkflowStep.TEMPLATE -> "২০টি JSON টেমপ্লেট · 20 JSON templates"
            ProjectWorkflowStep.AUTO_MATCH -> "SRT থেকে সিন তৈরি · Auto split scenes"
            ProjectWorkflowStep.SCENES -> "লেয়ার, টেক্সট ও মোশন · Layers & motion"
            ProjectWorkflowStep.PREVIEW -> "ডিটারমিনিস্টিক প্রিভিউ · Studio preview"
            ProjectWorkflowStep.WATERMARK -> "ইউজার ও ডেভেলপার ওয়াটারমার্ক (OFF by default)"
            ProjectWorkflowStep.EXPORT -> "আসল H.264/AAC MP4 এক্সপোর্ট"
        }
    },
    aspectRatioHint = { "${it.orientationTagBn} · ${it.orientationTagEn}" },
)
