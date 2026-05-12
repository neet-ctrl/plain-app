import { ref, watch, nextTick, getCurrentInstance, onActivated, onDeactivated, type Ref } from 'vue'
import { gqlFetch, GqlError } from './gql-client'
import {
  chatItemFragment,
  chatChannelFragment,
  messageFragment,
  messageConversationFragment,
  contactFragment,
  callFragment,
  imageFragment,
  videoFragment,
  audioFragment,
  fileFragment,
  appFragment,
  tagFragment,
  noteFragment,
  feedFragment,
  feedEntryFragment,
  packageFragment,
  tagSubFragment,
  notificationFragment,
  deviceInfoFragment,
  bookmarkFragment,
  bookmarkGroupFragment,
  docFragment,
  deviceLocationFragment,
  blockedAppsStateFragment,
} from './fragments'

// --- Query Wrappers ---

function getErrorMessage(e: any): string {
  if (e instanceof GqlError) {
    if (e.status === 403) return 'web_access_disabled'
    return e.message
  }
  return 'network_error'
}

function resolveVars(variables: any): Record<string, any> | undefined {
  if (!variables) return undefined
  return typeof variables === 'function' ? variables() : variables
}

export interface InitQueryParams<TResult> {
  handle: (data: TResult, error: string) => void
  document: string
  variables?: any
  options?: any
}

export function initQuery<TResult = any>(params: InitQueryParams<TResult>) {
  const loading = ref(false)
  const result = ref<TResult>()

  async function execute(vars?: Record<string, any>) {
    loading.value = true
    try {
      const v = vars ?? resolveVars(params.variables)
      const r = await gqlFetch<TResult>(params.document, v)
      if (r.errors?.length) {
        params.handle(r.data, r.errors[0].message)
      } else {
        result.value = r.data
        params.handle(r.data, '')
      }
    } catch (e: any) {
      params.handle(undefined as any, getErrorMessage(e))
    } finally {
      loading.value = false
    }
  }

  execute()

  if (typeof params.variables === 'function') {
    // Guard watcher so deactivated keep-alive instances don't fire queries
    // when the shared reactive route changes.
    // nextTick defers execution until after KeepAlive lifecycle hooks complete.
    let active = true
    const inst = getCurrentInstance()
    if (inst) {
      onDeactivated(() => { active = false })
      onActivated(() => { active = true })
    }
    watch(params.variables, async () => {
      await nextTick()
      if (active) execute()
    }, { deep: true })
  }

  return { loading, result, refetch: execute }
}

export function initLazyQuery<TResult = any>(params: InitQueryParams<TResult>) {
  const loading = ref(false)
  const result = ref<TResult>()

  async function doFetch(vars?: Record<string, any>) {
    loading.value = true
    try {
      const v = vars ?? resolveVars(params.variables)
      const r = await gqlFetch<TResult>(params.document, v)
      if (r.errors?.length) {
        params.handle(r.data, r.errors[0].message)
      } else {
        result.value = r.data
        params.handle(r.data, '')
      }
    } catch (e: any) {
      params.handle(undefined as any, getErrorMessage(e))
    } finally {
      loading.value = false
    }
  }

  return { loading, result, fetch: doFetch }
}

// --- GraphQL Query Definitions ---

export const chatItemsGQL = `
  query ($id: String!) {
    chatItems(id: $id) {
      ...ChatItemFragment
    }
  }
  ${chatItemFragment}
`

export const peersGQL = `
  query {
    peers {
      id
      name
      ip
      status
      port
      deviceType
      createdAt
      updatedAt
    }
  }
`

export const appFilesGQL = `
  query appFiles($offset: Int!, $limit: Int!) {
    appFiles(offset: $offset, limit: $limit) {
      id
      size
      mimeType
      fileName
      createdAt
      updatedAt
    }
    appFileCount
  }
`

export const chatChannelsGQL = `
  query {
    chatChannels {
      ...ChatChannelFragment
    }
  }
  ${chatChannelFragment}
`

export const fileInfoGQL = `
  query ($id: ID!, $path: String!, $fileName: String!) {
    fileInfo(id: $id, path: $path, fileName: $fileName) {
      ... on FileInfo {
        path
        updatedAt
        size
        tags {
          ...TagSubFragment
        }
      }
      data {
        ... on ImageFileInfo {
          width
          height
          location {
            latitude
            longitude
          }
        }
        ... on VideoFileInfo {
          duration
          width
          height
          location {
            latitude
            longitude
          }
        }
        ... on AudioFileInfo {
          duration
          location {
            latitude
            longitude
          }
        }
      }
    }
  }
  ${tagSubFragment}
`

export const smsGQL = `
  query sms($offset: Int!, $limit: Int!, $query: String!) {
    sms(offset: $offset, limit: $limit, query: $query) {
      ...MessageFragment
    }
    smsCount(query: $query)
  }
  ${messageFragment}
`

export const smsConversationsGQL = `
  query smsConversations($offset: Int!, $limit: Int!, $query: String!) {
    smsConversations(offset: $offset, limit: $limit, query: $query) {
      ...MessageConversationFragment
    }
    smsConversationCount(query: $query)
  }
  ${messageConversationFragment}
`

export const contactsGQL = `
  query contacts($offset: Int!, $limit: Int!, $query: String!) {
    contacts(offset: $offset, limit: $limit, query: $query) {
      ...ContactFragment
    }
    contactCount(query: $query)
  }
  ${contactFragment}
`

export const homeStatsGQL = `
  query homeStats($mediaQuery: String!) {
    smsCount(query: "")
    contactCount(query: "")
    callCount(query: "")
    imageCount(query: $mediaQuery)
    audioCount(query: $mediaQuery)
    videoCount(query: $mediaQuery)
    packageCount(query: "")
    noteCount(query: "")
    docCount(query: "")
    feedEntryCount(query: "")
    mounts {
      id
      path
      mountPoint
      totalBytes
      freeBytes
      driveType
    }
  }
`

export const contactSourcesGQL = `
  query {
    contactSources {
      name
      type
    }
  }
`

export const callsGQL = `
  query calls($offset: Int!, $limit: Int!, $query: String!) {
    calls(offset: $offset, limit: $limit, query: $query) {
      ...CallFragment
    }
    callCount(query: $query)
  }
  ${callFragment}
`

export const imagesGQL = `
  query images($offset: Int!, $limit: Int!, $query: String!, $sortBy: FileSortBy!) {
    images(offset: $offset, limit: $limit, query: $query, sortBy: $sortBy) {
      ...ImageFragment
    }
    imageCount(query: $query)
  }
  ${imageFragment}
`

export const imageSearchStatusGQL = `
  query {
    imageSearchStatus {
      status
      downloadProgress
      errorMessage
      modelSize
      modelDir
      isIndexing
      totalImages
      indexedImages
    }
  }
`

export const videosGQL = `
  query videos($offset: Int!, $limit: Int!, $query: String!, $sortBy: FileSortBy!) {
    videos(offset: $offset, limit: $limit, query: $query, sortBy: $sortBy) {
      ...VideoFragment
    }
    videoCount(query: $query)
  }
  ${videoFragment}
`

export const audiosGQL = `
  query audios($offset: Int!, $limit: Int!, $query: String!, $sortBy: FileSortBy!) {
    items: audios(offset: $offset, limit: $limit, query: $query, sortBy: $sortBy) {
      ...AudioFragment
    }
    total: audioCount(query: $query)
  }
  ${audioFragment}
`

export const filesGQL = `
  query files($root: String!, $offset: Int!, $limit: Int!, $query: String!, $sortBy: FileSortBy!) {
    files(root: $root, offset: $offset, limit: $limit, query: $query, sortBy: $sortBy) {
      ...FileFragment
    }
  }
  ${fileFragment}
`

export const recentFilesGQL = `
  query recentFiles {
    recentFiles {
      ...FileFragment
    }
  }
  ${fileFragment}
`

export const mountsGQL = `
  query {
    mounts {
      id
      name
      path
      mountPoint
      fsType
      totalBytes
      usedBytes
      freeBytes
      remote
      alias
      driveType
      diskID
    }
  }
`

export const appGQL = `
  query {
    app {
      ...AppFragment
    }
  }
  ${appFragment}
`

export const securityQAGQL = `
  query {
    securityQA {
      question
      hasAnswer
    }
  }
`

export const tagsGQL = `
  query tags($type: DataType!) {
    tags(type: $type) {
      ...TagFragment
    }
  }
  ${tagFragment}
`

export const mediaBucketsGQL = `
  query mediaBuckets($type: DataType!) {
    mediaBuckets(type: $type) {
      id
      name
      itemCount
      topItems
    }
  }
`

export const notesGQL = `
  query notes($offset: Int!, $limit: Int!, $query: String!) {
    notes(offset: $offset, limit: $limit, query: $query) {
      id
      title
      isPrivate
      encryptedBlob
      deletedAt
      createdAt
      updatedAt
      tags {
        ...TagSubFragment
      }
    }
    noteCount(query: $query)
  }
  ${tagSubFragment}
`

export const noteGQL = `
  query note($id: ID!) {
    note(id: $id) {
      ...NoteFragment
    }
  }
  ${noteFragment}
`

export const feedsGQL = `
  query {
    feeds {
      ...FeedFragment
    }
  }
  ${feedFragment}
`

export const feedEntriesGQL = `
  query feedEntries($offset: Int!, $limit: Int!, $query: String!) {
    items: feedEntries(offset: $offset, limit: $limit, query: $query) {
      id
      title
      url
      image
      author
      feedId
      rawId
      publishedAt
      createdAt
      updatedAt
      tags {
        ...TagSubFragment
      }
    }
    total: feedEntryCount(query: $query)
  }
  ${tagSubFragment}
`

export const feedsTagsGQL = `
  query feedsTags($type: DataType!) {
    tags(type: $type) {
      ...TagFragment
    }
    feeds {
      ...FeedFragment
    }
  }
  ${feedFragment}
  ${tagFragment}
`

export const bucketsTagsGQL = `
  query bucketsTags($type: DataType!) {
    tags(type: $type) {
      ...TagFragment
    }
    mediaBuckets(type: $type) {
      id
      name
      itemCount
      topItems
    }
  }
  ${tagFragment}
`

export const feedEntryGQL = `
  query feedEntry($id: ID!) {
    feedEntry(id: $id) {
      ...FeedEntryFragment
      feed {
        ...FeedFragment
      }
    }
  }
  ${feedFragment}
  ${feedEntryFragment}
`

export const imageCountGQL = `
  query imageCount($query: String!) {
    total: imageCount(query: $query)
    trash: imageCount(query: "trash:true")
  }
`

export const audioCountGQL = `
  query audioCount($query: String!) {
    total: audioCount(query: $query)
    trash: audioCount(query: "trash:true")
  }
`

export const docsGQL = `
  query docs($offset: Int!, $limit: Int!, $query: String!, $sortBy: FileSortBy!) {
    items: docs(offset: $offset, limit: $limit, query: $query, sortBy: $sortBy) {
      ...DocFragment
    }
    total: docCount(query: $query)
  }
  ${docFragment}
`

export const docCountGQL = `
  query {
    total: docCount(query: "")
    extGroups: docExtGroups {
      ext
      count
    }
  }
`

export const videoCountGQL = `
  query videoCount($query: String!) {
    total: videoCount(query: $query)
    trash: videoCount(query: "trash:true")
  }
`

export const packageCountGQL = `
  query {
    total: packageCount(query: "")
    system: packageCount(query: "type:system")
  }
`

export const feedEntryCountGQL = `
  query {
    total: feedEntryCount(query: "")
    today: feedEntryCount(query: "today:true")
    feedsCount {
      id
      count
    }
  }
`

export const contactCountGQL = `
  query {
    total: contactCount(query: "")
  }
`

export const callCountGQL = `
  query {
    total: callCount(query: "")
    incoming: callCount(query: "type:1")
    outgoing: callCount(query: "type:2")
    missed: callCount(query: "type:3")
  }
`

export const smsCountGQL = `
  query {
    smsAllCounts {
      total
      inbox
      sent
      drafts
    }
  }
`

export const archivedConversationsGQL = `
  query {
    archivedConversations {
      ...MessageConversationFragment
    }
  }
  ${messageConversationFragment}
`

export const noteCountGQL = `
  query {
    total: noteCount(query: "")
    trash: noteCount(query: "trash:true")
  }
`

export const packagesGQL = `
  query packages($offset: Int!, $limit: Int!, $query: String!, $sortBy: FileSortBy!) {
    packages(offset: $offset, limit: $limit, query: $query, sortBy: $sortBy) {
      ...PackageFragment
    }
    packageCount(query: $query)
  }
  ${packageFragment}
`

export const packageStatusesGQL = `
  query packageStatuses($ids: [ID!]!) {
    packageStatuses(ids: $ids) {
      id
      exist
      updatedAt
    }
  }
`

export const screenMirrorStateGQL = `
  query {
    screenMirrorState
    screenMirrorControlEnabled
    screenMirrorQuality {
      mode
      resolution
    }
  }
`

export const screenMirrorControlEnabledGQL = `
  query {
    screenMirrorControlEnabled
  }
`

export const liveCameraStateGQL = `
  query {
    liveCameraState {
      running
      facing
      hasPermission
    }
  }
`

export const liveMicStateGQL = `
  query {
    liveMicState {
      running
      muted
      hasPermission
    }
  }
`

export const screenMirrorQualityGQL = `
  query {
    screenMirrorQuality {
      mode
    }
  }
`

export const notificationsGQL = `
  query {
    notifications {
      ...NotificationFragment
    }
  }
  ${notificationFragment}
`

export const deviceInfoGQL = `
  query {
    deviceInfo {
      ...DeviceInfoFragment
    }
    battery {
      level
      voltage
      health
      plugged
      temperature
      status
      technology
      capacity
    }
  }
  ${deviceInfoFragment}
`

export const uploadedChunksGQL = `
  query uploadedChunks($fileId: String!) {
    uploadedChunks(fileId: $fileId)
  }
`

export const pomodoroSettingsGQL = `
  query {
    pomodoroSettings {
      workDuration
      shortBreakDuration
      longBreakDuration
      pomodorosBeforeLongBreak
      showNotification
      playSoundOnComplete
    }
  }
`

export const pomodoroTodayAndSettingsGQL = `
  query {
    pomodoroToday {
      date
      completedCount
      currentRound
      timeLeft
      totalTime
      isRunning
      isPause
      state
    }
    pomodoroSettings {
      workDuration
      shortBreakDuration
      longBreakDuration
      pomodorosBeforeLongBreak
      showNotification
      playSoundOnComplete
    }
  }
`

export const bookmarksGQL = `
  query {
    bookmarks {
      ...BookmarkFragment
    }
    bookmarkGroups {
      ...BookmarkGroupFragment
    }
  }
  ${bookmarkFragment}
  ${bookmarkGroupFragment}
`

// --- Utilities queries ---

export const volumesGQL = `
  query { volumes { stream percent } }
`

export const brightnessGQL = `
  query { brightness }
`

export const torchOnGQL = `
  query { torchOn }
`

export const locateRunningGQL = `
  query { locateRunning }
`

export const deviceLocationGQL = `
  query {
    deviceLocation { ...DeviceLocationFragment }
  }
  ${deviceLocationFragment}
`

export const blockedAppsStateGQL = `
  query {
    blockedAppsState { ...BlockedAppsStateFragment }
  }
  ${blockedAppsStateFragment}
`

export const launchHistoryGQL = `
  query launchHistory($limit: Int!) {
    launchHistory(limit: $limit) { packageId appName timestamp }
  }
`

export const notificationLogGQL = `
  query {
    notificationLog { ...NotificationFragment }
  }
  ${notificationFragment}
`

export const timelineEntriesGQL = `
  query timelineEntries($limit: Int!) {
    timelineEntries(limit: $limit) { id type title subtitle appId appName time }
    timelineStartedAt
  }
`

export const liveCallStateGQL = `
  query {
    liveCallState { state direction source appId appName display startedAt acceptedAt muted silenced }
  }
`

export const callRecorderStateGQL = `
  query {
    callRecorderState {
      enabled recording currentDisplayName currentSource currentStartedAt
      totalCount totalSize lastError activeAudioSource speakerphoneForced
    }
  }
`

export const callRecordingsGQL = `
  query callRecordings($offset: Int!, $limit: Int!) {
    callRecordings(offset: $offset, limit: $limit) {
      id filename displayName source direction appId appName
      startedAt endedAt durationMs sizeBytes fileId audioSource speakerphoneForced
    }
    callRecordingsCount
  }
`

export const liveCapturesGQL = `
  query liveCaptures($offset: Int!, $limit: Int!, $source: String) {
    liveCaptures(offset: $offset, limit: $limit, source: $source) {
      id filename source kind mimeType createdAt durationMs sizeBytes fileId
    }
    liveCapturesCount(source: $source)
    liveCapturesTotalSize(source: $source)
  }
`

export const locationTrackingStateGQL = `
  query locationTrackingState {
    locationTrackingState {
      enabled running intervalSec minDisplacement totalPoints
      latest { ts lat lng accuracy speed altitude bearing battery charging provider }
    }
  }
`

export const locationPointsGQL = `
  query locationPoints($offset: Int!, $limit: Int!, $fromTs: String!, $toTs: String!) {
    locationPoints(offset: $offset, limit: $limit, fromTs: $fromTs, toTs: $toTs) {
      ts lat lng accuracy speed altitude bearing battery charging provider
    }
  }
`

export const locationPointsCountGQL = `
  query locationPointsCount($fromTs: String!, $toTs: String!) {
    locationPointsCount(fromTs: $fromTs, toTs: $toTs)
  }
`

export const geofencesGQL = `
  query geofences {
    geofences {
      id name lat lng radius color enabled
      triggerEnter triggerExit
      actionRecordAudio recordAudioSec
      actionNotifyWeb
      actionLockApps lockedAppIds lockAppsDurationSec
      customNote createdAt currentlyInside
    }
  }
`

export const geofenceEventsGQL = `
  query geofenceEvents($offset: Int!, $limit: Int!, $geofenceId: String!) {
    geofenceEvents(offset: $offset, limit: $limit, geofenceId: $geofenceId) {
      id geofenceId geofenceName type ts lat lng accuracy batteryLevel
      recordingFile recordingDurationMs notifiedWeb lockedApps
    }
  }
`

export const geofenceAudiosGQL = `
  query geofenceAudios($offset: Int!, $limit: Int!, $geofenceId: String!) {
    geofenceAudios(offset: $offset, limit: $limit, geofenceId: $geofenceId) {
      id geofenceId geofenceName eventId ts durationMs sizeBytes fileId
    }
  }
`

export const keystrokeStateGQL = `
  query keystrokeState {
    keystrokeState {
      enabled accessibilityServiceConnected bufferLimit totalEntries
    }
  }
`

export const keystrokeEntriesGQL = `
  query keystrokeEntries($offset: Int!, $limit: Int!, $query: String!, $packageName: String!, $fromTs: String!, $toTs: String!) {
    keystrokeEntries(offset: $offset, limit: $limit, query: $query, packageName: $packageName, fromTs: $fromTs, toTs: $toTs) {
      id ts packageName appLabel fieldHint text
    }
  }
`

export const keystrokeEntriesCountGQL = `
  query keystrokeEntriesCount($query: String!, $packageName: String!, $fromTs: String!, $toTs: String!) {
    keystrokeEntriesCount(query: $query, packageName: $packageName, fromTs: $fromTs, toTs: $toTs)
  }
`

export const keystrokePackageStatsGQL = `
  query keystrokePackageStats {
    keystrokePackageStats { packageName count }
  }
`

export const stealthScreenshotStateGQL = `
  query stealthScreenshotState {
    stealthScreenshotState {
      enabled accessibilityServiceConnected supportedByOs
      intervalMin keepCount totalShots
    }
  }
`

export const stealthScreenshotsGQL = `
  query stealthScreenshots($offset: Int!, $limit: Int!) {
    stealthScreenshots(offset: $offset, limit: $limit) {
      id ts packageName appLabel width height sizeBytes manual fileId
    }
  }
`

// ===== Device Control Hub =====

export const launchAppsGQL = `
  query launchApps($query: String!) {
    launchApps(query: $query) {
      packageName label versionName isSystem installedAt updatedAt launchable
    }
  }
`

export const networkUsageGQL = `
  query networkUsage($windowDays: Int!) {
    networkUsage(windowDays: $windowDays) {
      sinceMs untilMs totalRx totalTx activeNetwork usageAccessGranted
      apps { packageName label rxBytes txBytes rxBytesWifi txBytesWifi rxBytesMobile txBytesMobile }
    }
  }
`

export const wifiStateGQL = `
  query wifiState {
    wifiState {
      enabled connectedSsid connectedBssid rssi linkSpeedMbps frequencyMhz
      ipv4 hotspotState savedListAccessible canScan
    }
  }
`

export const wifiScanGQL = `
  query wifiScan {
    wifiScan {
      ssid bssid capabilities frequencyMhz rssi channelWidth seenMs isCurrent
    }
  }
`

export const bluetoothStateGQL = `
  query bluetoothState {
    bluetoothState {
      supported enabled scanning hasScanPermission hasConnectPermission
      pairedCount nearbyCount
    }
  }
`

export const bluetoothPairedGQL = `
  query bluetoothPaired {
    bluetoothPaired {
      address name type bondState rssi firstSeenMs lastSeenMs nearby
    }
  }
`

export const bluetoothNearbyGQL = `
  query bluetoothNearby {
    bluetoothNearby {
      address name type bondState rssi firstSeenMs lastSeenMs nearby
    }
  }
`

export const batteryHistoryGQL = `
  query batteryHistory($days: Int!) {
    batteryHistory(days: $days) {
      sinceMs untilMs charging currentLevel plugged
      samples { ts level plugged temperatureC voltageMv status }
    }
  }
`

export const packetCaptureStateGQL = `
  query packetCaptureState {
    packetCaptureState {
      supported enabled running totalEntries needsConsent
    }
  }
`

export const packetEntriesGQL = `
  query packetEntries($offset: Int!, $limit: Int!, $host: String!) {
    packetEntries(offset: $offset, limit: $limit, host: $host) {
      id ts host port protocol appPackage appLabel sizeBytes resolvedIp
    }
  }
`

// Automation
export const automationStateGQL = `
  query automationState {
    automationState { enabled ruleCount activeCount nextScheduledMs }
  }
`

const ruleFields = `
  id name enabled kind cooldownMs lastRunMs createdMs updatedMs
  trigger { type params { key value } }
  conditions { type params { key value } }
  actions { type params { key value } }
`

export const automationRulesGQL = `
  query automationRules { automationRules { ${ruleFields} } }
`

export const automationRunsGQL = `
  query automationRuns($limit: Int!) {
    automationRuns(limit: $limit) { id ruleId ruleName ts ok source log }
  }
`

export const upsertAutomationRuleGQL = `
  mutation upsertAutomationRule($input: AutomationRuleInput!) {
    upsertAutomationRule(input: $input) { ${ruleFields} }
  }
`

// Preferred wire format. Avoids kgraphql's nested-input deserialization issues
// by sending the entire rule as a single JSON string. See parseRuleJson() in
// DeviceControlGraphQL.kt for the expected shape.
export const upsertAutomationRuleJsonGQL = `
  mutation upsertAutomationRuleJson($ruleJson: String!) {
    upsertAutomationRuleJson(ruleJson: $ruleJson) { ${ruleFields} }
  }
`

export const setAutomationRuleEnabledGQL = `
  mutation setAutomationRuleEnabled($id: String!, $enabled: Boolean!) {
    setAutomationRuleEnabled(id: $id, enabled: $enabled)
  }
`

export const deleteAutomationRuleGQL = `
  mutation deleteAutomationRule($id: String!) { deleteAutomationRule(id: $id) }
`

export const runAutomationRuleGQL = `
  mutation runAutomationRule($id: String!) { runAutomationRule(id: $id) }
`

export const setAutomationEnabledGQL = `
  mutation setAutomationEnabled($enabled: Boolean!) { setAutomationEnabled(enabled: $enabled) }
`

export const clearAutomationRunsGQL = `
  mutation clearAutomationRuns { clearAutomationRuns }
`

export const allPermissionsStatusGQL = `
  query {
    allPermissionsStatus { name label granted enabled category }
  }
`

export const accelerometerDataGQL = `
  query {
    accelerometerData {
      gravityX gravityY gravityZ
      motionX motionY motionZ
      angle vibrationMagnitude
    }
  }
`

export const soundLevelGQL = `
  query { soundLevel }
`

export const soundMeterRunningGQL = `
  query { soundMeterRunning }
`

export const torchPatternGQL = `
  query { torchPattern }
`

export const ambientLightGQL = `
  query { ambientLight }
`

// ─── New Sensor Queries ───────────────────────────────────────────────────────

export const gyroscopeDataGQL = `
  query {
    gyroscopeData { x y z magnitude }
  }
`

export const gyroscopeSupportedGQL = `
  query { gyroscopeSupported }
`

export const magnetometerDataGQL = `
  query {
    magnetometerData { x y z heading cardinalDir }
  }
`

export const magnetometerSupportedGQL = `
  query { magnetometerSupported }
`

export const barometerDataGQL = `
  query {
    barometerData { pressureHpa altitudeMeters }
  }
`

export const barometerSupportedGQL = `
  query { barometerSupported }
`

export const stepCountGQL = `
  query { stepCount }
`

export const pedometerSupportedGQL = `
  query { pedometerSupported }
`

export const proximityDataGQL = `
  query {
    proximityData { near distanceCm maxRangeCm supported }
  }
`

export const ambientTemperatureGQL = `
  query { ambientTemperature }
`

export const temperatureSupportedGQL = `
  query { temperatureSupported }
`

export const heartRateGQL = `
  query { heartRate }
`

export const heartRateSupportedGQL = `
  query { heartRateSupported }
`

// ─── New System Control Queries ───────────────────────────────────────────────

export const dndStatusGQL = `
  query {
    dndStatus { mode modeLabel policyGranted }
  }
`

export const airplaneModeGQL = `
  query { airplaneMode }
`

export const hotspotStatusGQL = `
  query {
    hotspotStatus { enabled }
  }
`

export const storageBreakdownGQL = `
  query {
    storageBreakdown {
      totalBytes usedBytes freeBytes
      appsBytes imagesBytes videosBytes audioBytes documentsBytes otherBytes cacheBytes
    }
  }
`

export const runningProcessesGQL = `
  query {
    runningProcesses {
      pid processName appLabel packageName importance importanceLabel rssKb
    }
  }
`

export const clipboardTextGQL = `
  query { clipboardText }
`

export const generateQrCodeGQL = `
  query generateQrCode($text: String!) {
    generateQrCode(text: $text)
  }
`

export const networkInterfacesGQL = `
  query {
    networkInterfaces { name addresses isUp isLoopback }
  }
`

export const triggerQrScanGQL = `
  query { triggerQrScan }
`

export const vpnStatusGQL = `
  query {
    vpnStatus {
      isConnected
      vpnName
      vpnPackage
      vpnIp
      vpnType
      mtu
      interfaces { name address }
    }
  }
`

export const scheduledSmsListGQL = `
  query {
    scheduledSmsList {
      id
      recipient
      message
      sendAt
      sent
      overdue
    }
  }
`

export const simInfoGQL = `
  query {
    simInfo {
      slotIndex
      carrierName
      operatorName
      phoneNumber
      networkTypeName
      mcc
      mnc
      isRoaming
      isDataActive
      signalBars
      simState
      iccid
    }
  }
`

export const protectedPermissionsStatusGQL = `
  query {
    protectedPermissionsStatus {
      name
      label
      description
      features
      adbCommand
      grantType
      granted
      settingsPath
    }
  }
`

export const deviceOwnerStatusGQL = `
  query {
    deviceOwnerStatus {
      isDeviceOwner
      isDeviceAdmin
      uninstallBlocked
      kioskEnabled
      cameraDisabled
      bluetoothDisabled
      usbDebuggingEnabled
      globalProxy
      maxFailedPasswordsForWipe
      grantablePermissionsCount
      alreadyGrantedCount
    }
  }
`
