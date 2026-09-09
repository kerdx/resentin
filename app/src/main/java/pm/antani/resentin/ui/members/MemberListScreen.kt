package pm.antani.resentin.ui.members

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.irc.SIGIL_PRIORITY
import pm.antani.resentin.irc.highestSigil
import pm.antani.resentin.ui.common.UserCardSheet
import pm.antani.resentin.ui.common.colorForNick
import pm.antani.resentin.ui.common.sigilsOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MemberListScreen(
    viewModel: MembersViewModel,
    title: String,
    networkSlug: String,
    viewerUsername: String,
    onBack: () -> Unit,
    onOpenQuery: (networkSlug: String, nick: String) -> Unit,
) {
    val members by viewModel.members.collectAsState()
    val ownSigils by viewModel.ownSigils.collectAsState()
    val privilegeModes by viewModel.privilegeModes.collectAsState()
    val whois by viewModel.selectedWhois.collectAsState()
    val coloredNicklist by viewModel.coloredNicklist.collectAsState()
    var query by remember { mutableStateOf("") }
    val sorted = remember(members) { members.sortedWith(memberOrdering) }
    val filtered = remember(sorted, query) {
        val q = query.trim()
        if (q.isEmpty()) sorted else sorted.filter { it.nick.contains(q, ignoreCase = true) }
    }
    // Role sections in display order, skipping whichever are empty (e.g. a channel
    // with no halfops never shows that header). Within a section the shared
    // memberOrdering still applies, so this stays consistent with the ungrouped sort.
    val groups = remember(filtered) {
        MemberGroup.entries.mapNotNull { group ->
            val list = filtered.filter { memberGroupOf(it) == group }
            if (list.isEmpty()) null else group to list
        }
    }
    val memberCountLabel = pluralStringResource(R.plurals.member_count, sorted.size, sorted.size)

    LaunchedEffect(Unit) {
        viewModel.navigateToQuery.collect { nick -> onOpenQuery(networkSlug, nick) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.members_title_with_count, title, memberCountLabel)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.members_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_cancel))
                        }
                    }
                },
                singleLine = true,
            )
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.members_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groups.forEach { (group, list) ->
                        stickyHeader(key = "group-${group.name}") {
                            Text(
                                text = stringResource(R.string.members_group_header, stringResource(group.labelRes), list.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(list, key = { it.nick }) { member ->
                            MemberRow(
                                member = member,
                                coloredNicklist = coloredNicklist,
                                roleLabel = group.roleRes?.let { stringResource(it) },
                                onClick = { viewModel.onMemberClick(member.nick) },
                            )
                        }
                    }
                }
            }
        }
    }

    val whoisValue = whois
    if (whoisValue != null) {
        val ignored by viewModel.isIgnored(whoisValue.target).collectAsState(initial = false)
        val avatar by viewModel.avatarBitmap.collectAsState()
        UserCardSheet(
            whois = whoisValue,
            viewerUsername = viewerUsername,
            ownSigils = ownSigils,
            targetSigils = viewModel.sigilsFor(whoisValue.target),
            availableModes = privilegeModes,
            onDismiss = viewModel::dismissWhois,
            onContactPrivately = viewModel::contactPrivately,
            onKick = viewModel::kick,
            onBan = viewModel::ban,
            onSetMode = viewModel::setMode,
            isIgnored = ignored,
            onIgnore = viewModel::ignore,
            onUnignore = viewModel::unignore,
            avatarBitmap = avatar,
        )
    }
}

@Composable
private fun MemberRow(
    member: MemberEntity,
    coloredNicklist: Boolean,
    roleLabel: String?,
    onClick: () -> Unit,
) {
    val nickColor = colorForNick(member.nick)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(nickColor.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = member.nick.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = nickColor,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.nick,
                style = MaterialTheme.typography.bodyLarge,
                color = if (coloredNicklist) nickColor else MaterialTheme.colorScheme.onSurface,
            )
            if (roleLabel != null) {
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = sigilsOf(member),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Privilege sections, highest first: owners/admins/ops together, then halfops, then
// voice, then everyone else. Decided by the member's HIGHEST sigil, same rule as
// memberOrdering — a "@+" nick counts as ops, not voice.
private enum class MemberGroup(val labelRes: Int, val roleRes: Int?) {
    OPS(R.string.members_group_ops, R.string.members_role_operator),
    HALFOP(R.string.members_group_halfops, R.string.members_role_halfop),
    VOICE(R.string.members_group_voiced, R.string.members_role_voice),
    USERS(R.string.members_group_users, null),
}

private fun memberGroupOf(member: MemberEntity): MemberGroup =
    when (highestSigil(sigilsOf(member))) {
        '~', '&', '@' -> MemberGroup.OPS
        '%' -> MemberGroup.HALFOP
        '+' -> MemberGroup.VOICE
        else -> MemberGroup.USERS
    }

// A member can hold several sigils at once (e.g. "@+"); rank by the highest one they
// have (SIGIL_PRIORITY), not by presence/absence of any single one.
private fun highestRank(member: MemberEntity): Int {
    val sigil = highestSigil(sigilsOf(member))
    return sigil?.let { SIGIL_PRIORITY.indexOf(it) } ?: SIGIL_PRIORITY.size
}

private val memberOrdering = compareBy<MemberEntity> { highestRank(it) }
    .thenBy { it.nick.lowercase() }
