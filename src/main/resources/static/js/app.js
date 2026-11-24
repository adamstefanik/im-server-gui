/**
 * Instant Message Server - Web GUI Application Logic
 * Pure Vanilla JavaScript (Zero external dependencies)
 */

(function () {
    // State
    const state = {
        socket: null,
        isConnected: false,
        myUserName: null,
        myGroups: new Set(['public']),
        activeView: { type: 'room', target: 'public' }, // { type: 'room' | 'private', target: string }
        onlineUsers: [],
        allRooms: ['public'],
        messages: {
            'room:public': []
        },
        unreadCounts: {}
    };

    // DOM Elements
    const elements = {
        connectionStatus: document.getElementById('connectionStatus'),
        userProfile: document.getElementById('userProfile'),
        navAvatar: document.getElementById('navAvatar'),
        navUserName: document.getElementById('navUserName'),
        btnChangeName: document.getElementById('btnChangeName'),
        btnHelp: document.getElementById('btnHelp'),
        
        roomsList: document.getElementById('roomsList'),
        btnJoinRoomModal: document.getElementById('btnJoinRoomModal'),
        usersList: document.getElementById('usersList'),
        userCount: document.getElementById('userCount'),
        
        chatTargetTitle: document.getElementById('chatTargetTitle'),
        targetName: document.getElementById('targetName'),
        chatTargetDesc: document.getElementById('chatTargetDesc'),
        btnClearChat: document.getElementById('btnClearChat'),
        messagesContainer: document.getElementById('messagesContainer'),
        
        chatForm: document.getElementById('chatForm'),
        messageInput: document.getElementById('messageInput'),
        quickChips: document.querySelectorAll('.qc-chip'),
        
        nameModal: document.getElementById('nameModal'),
        nameForm: document.getElementById('nameForm'),
        nicknameInput: document.getElementById('nicknameInput'),
        nameError: document.getElementById('nameError'),
        btnCancelNameModal: document.getElementById('btnCancelNameModal'),
        nameModalTitle: document.getElementById('nameModalTitle'),
        nameModalSubtitle: document.getElementById('nameModalSubtitle'),
        
        roomModal: document.getElementById('roomModal'),
        roomForm: document.getElementById('roomForm'),
        roomInput: document.getElementById('roomInput'),
        roomError: document.getElementById('roomError'),
        btnCancelRoomModal: document.getElementById('btnCancelRoomModal'),
        btnCloseRoomModal: document.getElementById('btnCloseRoomModal'),
        
        helpModal: document.getElementById('helpModal'),
        btnCloseHelpModal: document.getElementById('btnCloseHelpModal')
    };

    // Initialize
    function init() {
        setupEventListeners();
        connectWebSocket();
    }

    // WebSocket Management
    function connectWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws`;

        updateConnectionStatus('connecting');

        try {
            state.socket = new WebSocket(wsUrl);

            state.socket.onopen = () => {
                state.isConnected = true;
                updateConnectionStatus('connected');
            };

            state.socket.onmessage = (event) => {
                handleServerMessage(event.data);
            };

            state.socket.onclose = () => {
                state.isConnected = false;
                updateConnectionStatus('disconnected');
                setTimeout(connectWebSocket, 3000);
            };

            state.socket.onerror = (err) => {
                console.error('WebSocket Error:', err);
                state.socket.close();
            };
        } catch (e) {
            console.error('Failed to create WebSocket:', e);
            updateConnectionStatus('disconnected');
            setTimeout(connectWebSocket, 3000);
        }
    }

    // Message Router from Server
    function handleServerMessage(raw) {
        let envelope;
        try {
            envelope = JSON.parse(raw);
        } catch (e) {
            // Raw text fallback
            addSystemMessage(raw);
            return;
        }

        const type = envelope.type;
        const data = envelope.data;

        switch (type) {
            case 'CONNECTED':
                if (!state.myUserName) {
                    showNameModal(false);
                }
                break;

            case 'NAME_SET':
                state.myUserName = data.name;
                if (data.groups && Array.isArray(data.groups)) {
                    state.myGroups = new Set(data.groups);
                }
                updateUserProfileNav();
                hideNameModal();
                renderRooms();
                addSystemMessage(`Nickname set to '${state.myUserName}'. Joined #public.`);
                break;

            case 'CHAT': {
                const room = data.room || 'public';
                const viewKey = `room:${room}`;
                ensureViewKey(viewKey);
                state.messages[viewKey].push(data);

                if (state.activeView.type === 'room' && state.activeView.target === room) {
                    renderMessageBubble(data);
                    scrollToBottom();
                } else {
                    incrementUnread(viewKey);
                }
                break;
            }

            case 'PRIVATE': {
                const otherUser = data.isOutgoing ? data.recipient : data.sender;
                const viewKey = `private:${otherUser}`;
                ensureViewKey(viewKey);
                state.messages[viewKey].push(data);

                if (state.activeView.type === 'private' && state.activeView.target === otherUser) {
                    renderMessageBubble(data);
                    scrollToBottom();
                } else {
                    incrementUnread(viewKey);
                }
                break;
            }

            case 'USER_LIST':
                if (data.users) {
                    state.onlineUsers = data.users;
                    renderUsers();
                }
                if (data.rooms) {
                    state.allRooms = data.rooms;
                    renderRooms();
                }
                break;

            case 'ROOM_JOINED':
                state.myGroups.add(data.room);
                ensureViewKey(`room:${data.room}`);
                renderRooms();
                switchView('room', data.room);
                hideRoomModal();
                break;

            case 'ROOM_LEFT':
                state.myGroups.delete(data.room);
                renderRooms();
                if (state.activeView.type === 'room' && state.activeView.target === data.room) {
                    switchView('room', 'public');
                }
                break;

            case 'SYSTEM':
                addSystemMessage(data.text);
                break;

            case 'ERROR':
                handleServerError(data.message);
                break;

            case 'GROUPS_INFO':
                addSystemMessage(`Current groups: ${Array.from(data.groups).join(', ')}`);
                break;
        }
    }

    // Event Listeners
    function setupEventListeners() {
        // Chat submit
        elements.chatForm.addEventListener('submit', (e) => {
            e.preventDefault();
            sendMessage();
        });

        // Quick command chips
        elements.quickChips.forEach(chip => {
            chip.addEventListener('click', () => {
                const cmd = chip.getAttribute('data-cmd');
                elements.messageInput.value = cmd;
                elements.messageInput.focus();
            });
        });

        // Clear chat
        elements.btnClearChat.addEventListener('click', () => {
            const key = getViewKey(state.activeView.type, state.activeView.target);
            state.messages[key] = [];
            elements.messagesContainer.innerHTML = '';
        });

        // Modals
        elements.btnChangeName.addEventListener('click', () => showNameModal(true));
        elements.btnCancelNameModal.addEventListener('click', hideNameModal);
        elements.nameForm.addEventListener('submit', (e) => {
            e.preventDefault();
            submitName();
        });

        elements.btnJoinRoomModal.addEventListener('click', showRoomModal);
        elements.btnCancelRoomModal.addEventListener('click', hideRoomModal);
        elements.btnCloseRoomModal.addEventListener('click', hideRoomModal);
        elements.roomForm.addEventListener('submit', (e) => {
            e.preventDefault();
            submitJoinRoom();
        });

        elements.btnHelp.addEventListener('click', () => elements.helpModal.classList.remove('hidden'));
        elements.btnCloseHelpModal.addEventListener('click', () => elements.helpModal.classList.add('hidden'));

        // Close modal on background click
        [elements.nameModal, elements.roomModal, elements.helpModal].forEach(modal => {
            modal.addEventListener('click', (e) => {
                if (e.target === modal && !modal.classList.contains('hidden') && modal !== elements.nameModal) {
                    modal.classList.add('hidden');
                }
            });
        });
    }

    // Actions
    function sendMessage() {
        const text = elements.messageInput.value.trim();
        if (!text) return;

        if (!state.myUserName) {
            showNameModal(false);
            return;
        }

        // Raw Task 4 CLI commands
        if (text.startsWith('#')) {
            sendPayload({
                action: 'SEND_MESSAGE',
                text: text,
                room: state.activeView.type === 'room' ? state.activeView.target : 'public'
            });
            elements.messageInput.value = '';
            return;
        }

        // Direct Message view
        if (state.activeView.type === 'private') {
            sendPayload({
                action: 'SEND_PRIVATE',
                recipient: state.activeView.target,
                text: text
            });
        } else {
            // Room message
            sendPayload({
                action: 'SEND_MESSAGE',
                room: state.activeView.target,
                text: text
            });
        }

        elements.messageInput.value = '';
        elements.messageInput.focus();
    }

    function sendPayload(payload) {
        if (!state.isConnected || !state.socket) {
            alert('Not connected to IM server!');
            return;
        }
        state.socket.send(JSON.stringify(payload));
    }

    function submitName() {
        const name = elements.nicknameInput.value.trim();
        if (!name) {
            showNameError('Please enter a nickname.');
            return;
        }
        if (name.includes(' ')) {
            showNameError('Nickname must not contain spaces.');
            return;
        }
        sendPayload({ action: 'SET_NAME', name: name });
    }

    function submitJoinRoom() {
        const room = elements.roomInput.value.trim();
        if (!room) {
            showRoomError('Room name cannot be empty.');
            return;
        }
        if (room.includes(' ')) {
            showRoomError('Room name must not contain spaces.');
            return;
        }
        sendPayload({ action: 'JOIN_ROOM', room: room });
    }

    function leaveRoom(room) {
        if (room === 'public') {
            alert('Cannot leave the default #public room.');
            return;
        }
        sendPayload({ action: 'LEAVE_ROOM', room: room });
    }

    // View Switching
    function switchView(type, target) {
        state.activeView = { type, target };
        const viewKey = getViewKey(type, target);
        ensureViewKey(viewKey);
        state.unreadCounts[viewKey] = 0;

        // Update header
        if (type === 'room') {
            elements.chatTargetTitle.innerHTML = `<span class="target-prefix">#</span><span>${escapeHtml(target)}</span>`;
            elements.chatTargetDesc.textContent = target === 'public'
                ? 'Default public discussion room'
                : `Discussion room #${target}`;
            elements.messageInput.placeholder = `Message #${target} or type #command...`;
        } else {
            elements.chatTargetTitle.innerHTML = `<span class="target-prefix">@</span><span>${escapeHtml(target)}</span>`;
            elements.chatTargetDesc.textContent = `Direct private conversation with ${target}`;
            elements.messageInput.placeholder = `Private message to ${target}...`;
        }

        renderRooms();
        renderUsers();
        renderActiveViewMessages();
    }

    // Renderers
    function renderActiveViewMessages() {
        elements.messagesContainer.innerHTML = '';
        const key = getViewKey(state.activeView.type, state.activeView.target);
        const list = state.messages[key] || [];

        if (list.length === 0) {
            const emptyEl = document.createElement('div');
            emptyEl.className = 'system-welcome';
            emptyEl.innerHTML = `
                <div class="welcome-card">
                    <h3>No messages yet</h3>
                    <p>Start the conversation in <strong>${state.activeView.type === 'room' ? '#' : '@'}${escapeHtml(state.activeView.target)}</strong>.</p>
                </div>
            `;
            elements.messagesContainer.appendChild(emptyEl);
        } else {
            list.forEach(msg => renderMessageBubble(msg));
        }
        scrollToBottom();
    }

    function renderMessageBubble(msg) {
        // If system welcome placeholder is present, clear it
        const welcome = elements.messagesContainer.querySelector('.system-welcome');
        if (welcome) {
            welcome.remove();
        }

        const isPrivate = !!msg.isPrivate;
        const sender = msg.sender;
        const time = msg.timestamp || 'just now';
        const text = msg.text;
        const color = getAvatarColor(sender);

        const row = document.createElement('div');
        row.className = `message-row ${isPrivate ? 'msg-private' : ''}`;

        let privateTagHtml = '';
        if (isPrivate) {
            privateTagHtml = msg.isOutgoing
                ? `<span class="msg-private-badge">Direct to @${escapeHtml(msg.recipient)}</span>`
                : `<span class="msg-private-badge">Direct Message</span>`;
        }

        row.innerHTML = `
            <div class="msg-avatar" style="background-color: ${color}">
                ${escapeHtml(sender.charAt(0).toUpperCase())}
            </div>
            <div class="msg-body">
                <div class="msg-meta">
                    <span class="msg-sender">${escapeHtml(sender)}</span>
                    ${privateTagHtml}
                    <span class="msg-time">${escapeHtml(time)}</span>
                </div>
                <div class="msg-bubble">${escapeHtml(text)}</div>
            </div>
        `;

        elements.messagesContainer.appendChild(row);
    }

    function addSystemMessage(text) {
        const row = document.createElement('div');
        row.className = 'system-event';
        row.innerHTML = `<span class="system-pill">${escapeHtml(text)}</span>`;
        elements.messagesContainer.appendChild(row);
        scrollToBottom();
    }

    function renderRooms() {
        elements.roomsList.innerHTML = '';
        const sortedRooms = Array.from(state.myGroups).sort((a, b) => {
            if (a === 'public') return -1;
            if (b === 'public') return 1;
            return a.localeCompare(b);
        });

        sortedRooms.forEach(room => {
            const isActive = state.activeView.type === 'room' && state.activeView.target === room;
            const viewKey = `room:${room}`;
            const unread = state.unreadCounts[viewKey] || 0;

            const item = document.createElement('div');
            item.className = `room-item ${isActive ? 'active' : ''}`;

            const left = document.createElement('div');
            left.className = 'room-item-content';
            left.innerHTML = `
                <span class="room-prefix">#</span>
                <span class="room-name">${escapeHtml(room)}</span>
                ${unread > 0 ? `<span class="user-badge badge-web">${unread}</span>` : ''}
            `;

            item.appendChild(left);

            // Allow leaving room if not public
            if (room !== 'public') {
                const leaveBtn = document.createElement('button');
                leaveBtn.className = 'room-leave-btn';
                leaveBtn.textContent = '✕';
                leaveBtn.title = `Leave room #${room} (#leave)`;
                leaveBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    leaveRoom(room);
                });
                item.appendChild(leaveBtn);
            }

            item.addEventListener('click', () => switchView('room', room));
            elements.roomsList.appendChild(item);
        });
    }

    function renderUsers() {
        elements.usersList.innerHTML = '';
        elements.userCount.textContent = state.onlineUsers.length;

        if (state.onlineUsers.length === 0) {
            elements.usersList.innerHTML = `<div class="empty-state">No other users online</div>`;
            return;
        }

        state.onlineUsers.forEach(user => {
            const isSelf = user.name === state.myUserName;
            const isTarget = state.activeView.type === 'private' && state.activeView.target === user.name;
            const viewKey = `private:${user.name}`;
            const unread = state.unreadCounts[viewKey] || 0;
            const color = getAvatarColor(user.name);

            const item = document.createElement('div');
            item.className = `user-item ${isTarget ? 'room-item active' : ''}`;

            const left = document.createElement('div');
            left.className = 'user-item-left';
            left.innerHTML = `
                <div class="user-item-avatar" style="background-color: ${color}">
                    ${escapeHtml(user.name.charAt(0).toUpperCase())}
                </div>
                <span>${escapeHtml(user.name)}</span>
            `;

            const right = document.createElement('div');
            right.style.display = 'flex';
            right.style.alignItems = 'center';
            right.style.gap = '4px';

            if (isSelf) {
                right.innerHTML += `<span class="user-badge badge-self">You</span>`;
            } else {
                const clientBadge = user.clientType === 'TELNET'
                    ? `<span class="user-badge badge-telnet" title="Connected via TCP/Telnet">Telnet</span>`
                    : `<span class="user-badge badge-web" title="Connected via Web">Web</span>`;
                right.innerHTML += clientBadge;
            }

            if (unread > 0) {
                right.innerHTML += `<span class="user-badge badge-web">${unread}</span>`;
            }

            item.appendChild(left);
            item.appendChild(right);

            if (!isSelf) {
                item.title = `Send private message to @${user.name} (#sendPrivate)`;
                item.addEventListener('click', () => switchView('private', user.name));
            }

            elements.usersList.appendChild(item);
        });
    }

    function updateUserProfileNav() {
        if (state.myUserName) {
            elements.userProfile.classList.remove('hidden');
            elements.navUserName.textContent = state.myUserName;
            elements.navAvatar.textContent = state.myUserName.charAt(0).toUpperCase();
            elements.navAvatar.style.backgroundColor = getAvatarColor(state.myUserName);
        }
    }

    function updateConnectionStatus(status) {
        elements.connectionStatus.className = `status-pill status-${status}`;
        const label = elements.connectionStatus.querySelector('.status-label');
        if (status === 'connected') {
            label.textContent = 'Connected (:8080 | :33000)';
        } else if (status === 'connecting') {
            label.textContent = 'Connecting...';
        } else {
            label.textContent = 'Disconnected';
        }
    }

    // Modal Helpers
    function showNameModal(isChange) {
        elements.nameModal.classList.remove('hidden');
        elements.nameError.classList.add('hidden');
        elements.nicknameInput.value = state.myUserName || '';
        elements.nameModalTitle.textContent = isChange ? 'Change Nickname' : 'Choose Your Nickname';
        elements.nameModalSubtitle.textContent = isChange
            ? 'Enter a new unique nickname (spaces not allowed):'
            : 'Enter your nickname to join Instant Message Server:';
        if (isChange) {
            elements.btnCancelNameModal.classList.remove('hidden');
        } else {
            elements.btnCancelNameModal.classList.add('hidden');
        }
        setTimeout(() => elements.nicknameInput.focus(), 100);
    }

    function hideNameModal() {
        elements.nameModal.classList.add('hidden');
    }

    function showNameError(msg) {
        elements.nameError.textContent = msg;
        elements.nameError.classList.remove('hidden');
    }

    function showRoomModal() {
        elements.roomModal.classList.remove('hidden');
        elements.roomError.classList.add('hidden');
        elements.roomInput.value = '';
        setTimeout(() => elements.roomInput.focus(), 100);
    }

    function hideRoomModal() {
        elements.roomModal.classList.add('hidden');
    }

    function showRoomError(msg) {
        elements.roomError.textContent = msg;
        elements.roomError.classList.remove('hidden');
    }

    function handleServerError(msg) {
        if (!elements.nameModal.classList.contains('hidden')) {
            showNameError(msg);
        } else if (!elements.roomModal.classList.contains('hidden')) {
            showRoomError(msg);
        } else {
            alert(`Error: ${msg}`);
        }
    }

    // Utilities
    function getViewKey(type, target) {
        return `${type}:${target}`;
    }

    function ensureViewKey(key) {
        if (!state.messages[key]) {
            state.messages[key] = [];
        }
    }

    function incrementUnread(key) {
        state.unreadCounts[key] = (state.unreadCounts[key] || 0) + 1;
        renderRooms();
        renderUsers();
    }

    function scrollToBottom() {
        elements.messagesContainer.scrollTop = elements.messagesContainer.scrollHeight;
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function getAvatarColor(name) {
        const colors = [
            '#2f81f7', '#238636', '#da3633', '#8957e5',
            '#d29922', '#1f6feb', '#3fb950', '#a371f7'
        ];
        let hash = 0;
        for (let i = 0; i < (name || '').length; i++) {
            hash = name.charCodeAt(i) + ((hash << 5) - hash);
        }
        return colors[Math.abs(hash) % colors.length];
    }

    // Run
    document.addEventListener('DOMContentLoaded', init);
})();
