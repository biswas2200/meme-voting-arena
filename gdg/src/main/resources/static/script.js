// Meme Voting Arena - Frontend JavaScript
class MemeArenaApp {
    constructor() {
        this.API_BASE_URL = 'http://localhost:8080/api';
        this.currentUser = null;
        this.currentPage = 'home';
        this.currentMemes = [];
        this.currentPageNum = 0;
        this.pageSize = 12;
        this.hasMoreMemes = true;
        
        this.init();
    }
    
    init() {
        this.setupEventListeners();
        this.loadUserFromStorage();
        this.applyTheme();
        this.hideLoadingScreen();
        this.loadHomePage();
    }
    
    setupEventListeners() {
        // Navigation
        document.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const page = link.getAttribute('data-page');
                if (page) this.navigateToPage(page);
            });
        });
        
        // Theme toggle
        document.getElementById('theme-toggle-btn').addEventListener('click', () => {
            this.toggleTheme();
        });
        
        // Forms
        document.getElementById('login-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.handleLogin();
        });
        
        document.getElementById('register-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.handleRegister();
        });
        
        document.getElementById('upload-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.handleMemeUpload();
        });
        
        // Image preview
        document.getElementById('meme-url').addEventListener('input', (e) => {
            this.previewImage(e.target.value);
        });
        
        // Load more button
        document.getElementById('load-more-btn').addEventListener('click', () => {
            this.loadMoreMemes();
        });
        
        // Battle
        document.getElementById('start-battle-btn').addEventListener('click', () => {
            this.startBattle();
        });
        
        // Logout
        document.getElementById('logout-btn').addEventListener('click', () => {
            this.logout();
        });
        
        // Modal
        document.getElementById('modal').addEventListener('click', (e) => {
            if (e.target.id === 'modal') this.closeModal();
        });
        
        document.querySelector('.modal-close').addEventListener('click', () => {
            this.closeModal();
        });
        
        // Sort dropdown
        document.getElementById('sort-select').addEventListener('change', () => {
            this.reloadMemes();
        });
    }
    
    hideLoadingScreen() {
        setTimeout(() => {
            document.getElementById('loading-screen').style.opacity = '0';
            setTimeout(() => {
                document.getElementById('loading-screen').style.display = 'none';
            }, 500);
        }, 1000);
    }
    
    // Theme Management
    toggleTheme() {
        const currentTheme = document.documentElement.getAttribute('data-theme');
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        
        document.documentElement.setAttribute('data-theme', newTheme);
        document.getElementById('theme-toggle-btn').textContent = newTheme === 'dark' ? '☀️' : '🌙';
        
        localStorage.setItem('theme', newTheme);
    }
    
    applyTheme() {
        const savedTheme = localStorage.getItem('theme') || 'light';
        document.documentElement.setAttribute('data-theme', savedTheme);
        document.getElementById('theme-toggle-btn').textContent = savedTheme === 'dark' ? '☀️' : '🌙';
    }
    
    // Navigation
    navigateToPage(pageName) {
        // Hide all pages
        document.querySelectorAll('.page').forEach(page => {
            page.classList.remove('active');
        });
        
        // Show target page
        const targetPage = document.getElementById(`${pageName}-page`);
        if (targetPage) {
            targetPage.classList.add('active');
            this.currentPage = pageName;
            
            // Update navigation
            document.querySelectorAll('.nav-link').forEach(link => {
                link.classList.remove('active');
            });
            
            const activeLink = document.querySelector(`[data-page="${pageName}"]`);
            if (activeLink) activeLink.classList.add('active');
            
            // Load page-specific content
            this.loadPageContent(pageName);
        }
    }
    
    loadPageContent(pageName) {
        switch (pageName) {
            case 'home':
                this.loadHomePage();
                break;
            case 'feed':
                this.loadFeedPage();
                break;
            case 'leaderboard':
                this.loadLeaderboard();
                break;
            case 'profile':
                this.loadProfile();
                break;
            case 'battle':
                this.loadBattlePage();
                break;
        }
    }
    
    // Authentication
    loadUserFromStorage() {
        const token = localStorage.getItem('token');
        const userData = localStorage.getItem('userData');
        
        if (token && userData) {
            this.currentUser = JSON.parse(userData);
            this.updateNavigation();
        }
    }
    
    async handleLogin() {
        const username = document.getElementById('login-username').value;
        const password = document.getElementById('login-password').value;
        
        try {
            const response = await fetch(`${this.API_BASE_URL}/auth/signin`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ username, password })
            });
            
            if (response.ok) {
                const data = await response.json();
                this.currentUser = data;
                
                localStorage.setItem('token', data.token);
                localStorage.setItem('userData', JSON.stringify(data));
                
                this.updateNavigation();
                this.showToast('Login successful!', 'success');
                this.navigateToPage('home');
                
                // Clear form
                document.getElementById('login-form').reset();
            } else {
                const error = await response.json();
                this.showToast(error.message || 'Login failed', 'error');
            }
        } catch (error) {
            console.error('Login error:', error);
            this.showToast('Network error. Please try again.', 'error');
        }
    }
    
    async handleRegister() {
        const username = document.getElementById('register-username').value;
        const email = document.getElementById('register-email').value;
        const password = document.getElementById('register-password').value;
        
        try {
            const response = await fetch(`${this.API_BASE_URL}/auth/signup`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ username, email, password })
            });
            
            if (response.ok) {
                this.showToast('Registration successful! Please login.', 'success');
                this.navigateToPage('login');
                
                // Clear form
                document.getElementById('register-form').reset();
            } else {
                const error = await response.json();
                this.showToast(error.message || 'Registration failed', 'error');
            }
        } catch (error) {
            console.error('Registration error:', error);
            this.showToast('Network error. Please try again.', 'error');
        }
    }
    
    logout() {
        this.currentUser = null;
        localStorage.removeItem('token');
        localStorage.removeItem('userData');
        
        this.updateNavigation();
        this.showToast('Logged out successfully', 'success');
        this.navigateToPage('home');
    }
    
    updateNavigation() {
        const loginNav = document.getElementById('login-nav');
        const logoutBtn = document.getElementById('logout-btn');
        const uploadNav = document.getElementById('upload-nav');
        const profileNav = document.getElementById('profile-nav');
        
        if (this.currentUser) {
            loginNav.style.display = 'none';
            logoutBtn.style.display = 'block';
            uploadNav.style.display = 'block';
            profileNav.style.display = 'block';
        } else {
            loginNav.style.display = 'block';
            logoutBtn.style.display = 'none';
            uploadNav.style.display = 'none';
            profileNav.style.display = 'none';
        }
    }
    
    // API Helper
    async apiCall(endpoint, options = {}) {
        const token = localStorage.getItem('token');
        
        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json',
                ...(token && { 'Authorization': `Bearer ${token}` })
            }
        };
        
        const finalOptions = { ...defaultOptions, ...options };
        if (finalOptions.body && typeof finalOptions.body === 'object') {
            finalOptions.body = JSON.stringify(finalOptions.body);
        }
        
        try {
            const response = await fetch(`${this.API_BASE_URL}${endpoint}`, finalOptions);
            
            if (response.status === 401) {
                this.logout();
                throw new Error('Authentication required');
            }
            
            return response;
        } catch (error) {
            console.error('API call error:', error);
            throw error;
        }
    }
    
    // Home Page
    async loadHomePage() {
        if (this.currentUser) {
            document.getElementById('welcome-message').textContent = 
                `Welcome back, ${this.currentUser.username}! Ready to vote?`;
            
            try {
                const response = await this.apiCall('/auth/profile');
                if (response.ok) {
                    const profile = await response.json();
                    const keywordElement = document.getElementById('user-keyword');
                    keywordElement.textContent = `Your vibe: ${profile.keyword}`;
                    keywordElement.style.display = 'block';
                }
            } catch (error) {
                console.error('Error loading profile:', error);
            }
        } else {
            document.getElementById('welcome-message').textContent = 
                'Where the best memes rise to the top!';
            document.getElementById('user-keyword').style.display = 'none';
        }
        
        // Load stats
        await this.loadStats();
    }
    
    async loadStats() {
        try {
            const response = await this.apiCall('/memes?size=1000');
            if (response.ok) {
                const data = await response.json();
                document.getElementById('total-memes').textContent = data.totalElements || 0;
                
                // Calculate total votes
                const totalVotes = data.content?.reduce((sum, meme) => sum + Math.abs(meme.voteCount), 0) || 0;
                document.getElementById('total-votes').textContent = totalVotes;
            }
        } catch (error) {
            console.error('Error loading stats:', error);
        }
    }
    
    // Feed Page
    async loadFeedPage() {
        this.currentPageNum = 0;
        this.currentMemes = [];
        this.hasMoreMemes = true;
        
        document.getElementById('memes-grid').innerHTML = '';
        await this.loadMemes();
    }
    
    async reloadMemes() {
        this.currentPageNum = 0;
        this.currentMemes = [];
        this.hasMoreMemes = true;
        
        document.getElementById('memes-grid').innerHTML = '';
        await this.loadMemes();
    }
    
    async loadMemes() {
        try {
            const response = await this.apiCall(`/memes?page=${this.currentPageNum}&size=${this.pageSize}`);
            
            if (response.ok) {
                const data = await response.json();
                this.currentMemes = [...this.currentMemes, ...data.content];
                this.hasMoreMemes = !data.last;
                
                this.renderMemes(data.content);
                this.updateLoadMoreButton();
            }
        } catch (error) {
            console.error('Error loading memes:', error);
            this.showToast('Error loading memes', 'error');
        }
    }
    
    async loadMoreMemes() {
        this.currentPageNum++;
        await this.loadMemes();
    }
    
    renderMemes(memes) {
        const grid = document.getElementById('memes-grid');
        
        memes.forEach(meme => {
            const memeCard = this.createMemeCard(meme);
            grid.appendChild(memeCard);
        });
    }
    
    createMemeCard(meme) {
        const card = document.createElement('div');
        card.className = 'meme-card fade-in';
        
        const uploadDate = new Date(meme.uploadDate).toLocaleDateString();
        
        card.innerHTML = `
            <img src="${meme.imageUrl}" alt="${meme.title}" class="meme-image" onclick="memeApp.openMemeModal(${meme.id})">
            <div class="meme-content">
                <h3 class="meme-title">${meme.title}</h3>
                <p class="meme-meta">By ${meme.uploadedBy} • ${uploadDate}</p>
                <div class="meme-actions">
                    <div class="vote-buttons">
                        <button class="vote-btn upvote ${meme.userVoteType === 'UPVOTE' ? 'voted' : ''}" 
                                onclick="memeApp.voteMeme(${meme.id}, 'UPVOTE')" 
                                ${!this.currentUser ? 'disabled' : ''}>
                            👍 <span class="upvote-count">${this.getUpvoteCount(meme)}</span>
                        </button>
                        <button class="vote-btn downvote ${meme.userVoteType === 'DOWNVOTE' ? 'voted' : ''}" 
                                onclick="memeApp.voteMeme(${meme.id}, 'DOWNVOTE')"
                                ${!this.currentUser ? 'disabled' : ''}>
                            👎 <span class="downvote-count">${this.getDownvoteCount(meme)}</span>
                        </button>
                    </div>
                    <div class="vote-count">${meme.voteCount}</div>
                </div>
            </div>
        `;
        
        return card;
    }
    
    getUpvoteCount(meme) {
        // Simplified calculation - in real app this would come from backend
        return Math.max(0, meme.voteCount);
    }
    
    getDownvoteCount(meme) {
        // Simplified calculation - in real app this would come from backend
        return Math.max(0, -meme.voteCount);
    }
    
    updateLoadMoreButton() {
        const button = document.getElementById('load-more-btn');
        button.style.display = this.hasMoreMemes ? 'block' : 'none';
    }
    
    async voteMeme(memeId, voteType) {
        if (!this.currentUser) {
            this.showToast('Please login to vote', 'warning');
            return;
        }
        
        try {
            const response = await this.apiCall(`/memes/${memeId}/vote`, {
                method: 'PUT',
                body: { voteType }
            });
            
            if (response.ok) {
                const updatedMeme = await response.json();
                this.updateMemeCard(updatedMeme);
                this.showToast('Vote recorded!', 'success');
            } else {
                const error = await response.json();
                this.showToast(error.message || 'Voting failed', 'error');
            }
        } catch (error) {
            console.error('Voting error:', error);
            this.showToast('Network error. Please try again.', 'error');
        }
    }
    
    updateMemeCard(meme) {
        // Find and update the meme card
        const cards = document.querySelectorAll('.meme-card');
        cards.forEach(card => {
            const img = card.querySelector('.meme-image');
            if (img && img.getAttribute('onclick').includes(meme.id)) {
                // Update vote buttons
                const upvoteBtn = card.querySelector('.upvote');
                const downvoteBtn = card.querySelector('.downvote');
                const voteCount = card.querySelector('.vote-count');
                
                upvoteBtn.classList.toggle('voted', meme.userVoteType === 'UPVOTE');
                downvoteBtn.classList.toggle('voted', meme.userVoteType === 'DOWNVOTE');
                voteCount.textContent = meme.voteCount;
                
                // Update counts
                card.querySelector('.upvote-count').textContent = this.getUpvoteCount(meme);
                card.querySelector('.downvote-count').textContent = this.getDownvoteCount(meme);
            }
        });
    }
    
    // Leaderboard
    async loadLeaderboard() {
        try {
            const response = await this.apiCall('/memes/leaderboard');
            
            if (response.ok) {
                const leaderboard = await response.json();
                this.renderLeaderboard(leaderboard);
            }
        } catch (error) {
            console.error('Error loading leaderboard:', error);
            this.showToast('Error loading leaderboard', 'error');
        }
    }
    
    renderLeaderboard(memes) {
        const container = document.getElementById('leaderboard-content');
        container.innerHTML = '';
        
        memes.forEach((meme, index) => {
            const item = document.createElement('div');
            item.className = 'leaderboard-item fade-in';
            
            const uploadDate = new Date(meme.uploadDate).toLocaleDateString();
            
            item.innerHTML = `
                <div class="leaderboard-rank">${index + 1}</div>
                <div class="leaderboard-meme">
                    <img src="${meme.imageUrl}" alt="${meme.title}" class="leaderboard-image">
                    <div class="leaderboard-info">
                        <h3>${meme.title}</h3>
                        <p>By ${meme.uploadedBy} • ${uploadDate}</p>
                    </div>
                </div>
                <div class="leaderboard-votes">
                    <div class="vote-count">${meme.voteCount}</div>
                    <p>votes</p>
                </div>
            `;
            
            item.addEventListener('click', () => this.openMemeModal(meme.id));
            container.appendChild(item);
        });
    }
    
    // Battle Mode
    async loadBattlePage() {
        const arena = document.getElementById('battle-arena');
        arena.innerHTML = `
            <div class="battle-instruction">
                <p>Click on the meme you think is better!</p>
                <button id="start-battle-btn" class="btn btn-primary" onclick="memeApp.startBattle()">Start Battle</button>
            </div>
        `;
    }
    
    async startBattle() {
        if (!this.currentUser) {
            this.showToast('Please login to battle', 'warning');
            return;
        }
        
        try {
            const response = await this.apiCall('/memes/battle');
            
            if (response.ok) {
                const battleMemes = await response.json();
                if (battleMemes.length >= 2) {
                    this.renderBattle(battleMemes[0], battleMemes[1]);
                } else {
                    this.showToast('Not enough memes for battle', 'warning');
                }
            }
        } catch (error) {
            console.error('Error starting battle:', error);
            this.showToast('Error starting battle', 'error');
        }
    }
    
    renderBattle(meme1, meme2) {
        const arena = document.getElementById('battle-arena');
        arena.innerHTML = `
            <div class="battle-memes">
                <div class="battle-meme" onclick="memeApp.chooseBattleWinner(${meme1.id})">
                    <img src="${meme1.imageUrl}" alt="${meme1.title}">
                    <div class="battle-meme-info">
                        <h3>${meme1.title}</h3>
                        <p>Votes: ${meme1.voteCount}</p>
                    </div>
                </div>
                <div class="battle-meme" onclick="memeApp.chooseBattleWinner(${meme2.id})">
                    <img src="${meme2.imageUrl}" alt="${meme2.title}">
                    <div class="battle-meme-info">
                        <h3>${meme2.title}</h3>
                        <p>Votes: ${meme2.voteCount}</p>
                    </div>
                </div>
            </div>
            <div class="battle-result" style="display: none;">
                <h3>Great choice!</h3>
                <p>Your vote has been recorded.</p>
                <button class="btn btn-primary" onclick="memeApp.startBattle()">Battle Again</button>
            </div>
        `;
    }
    
    async chooseBattleWinner(memeId) {
        await this.voteMeme(memeId, 'UPVOTE');
        
        // Show result
        document.querySelector('.battle-memes').style.display = 'none';
        document.querySelector('.battle-result').style.display = 'block';
    }
    
    // Upload
    async handleMemeUpload() {
        if (!this.currentUser) {
            this.showToast('Please login to upload', 'warning');
            return;
        }
        
        const title = document.getElementById('meme-title').value;
        const imageUrl = document.getElementById('meme-url').value;
        
        try {
            const response = await this.apiCall('/memes', {
                method: 'POST',
                body: { title, imageUrl }
            });
            
            if (response.ok) {
                this.showToast('Meme uploaded successfully!', 'success');
                document.getElementById('upload-form').reset();
                document.getElementById('image-preview').innerHTML = '<p>Image preview will appear here</p>';
                this.navigateToPage('feed');
            } else {
                const error = await response.json();
                this.showToast(error.message || 'Upload failed', 'error');
            }
        } catch (error) {
            console.error('Upload error:', error);
            this.showToast('Network error. Please try again.', 'error');
        }
    }
    
    previewImage(url) {
        const preview = document.getElementById('image-preview');
        
        if (url) {
            preview.innerHTML = `<img src="${url}" alt="Preview" onload="this.style.display='block'" onerror="this.style.display='none'; this.parentElement.innerHTML='<p>Failed to load image</p>'">`;
        } else {
            preview.innerHTML = '<p>Image preview will appear here</p>';
        }
    }
    
    // Profile
    async loadProfile() {
        if (!this.currentUser) {
            this.navigateToPage('login');
            return;
        }
        
        try {
            const response = await this.apiCall('/auth/profile');
            
            if (response.ok) {
                const profile = await response.json();
                this.renderProfile(profile);
            }
        } catch (error) {
            console.error('Error loading profile:', error);
            this.showToast('Error loading profile', 'error');
        }
    }
    
    renderProfile(profile) {
        const container = document.getElementById('profile-content');
        const joinDate = new Date(profile.createdAt).toLocaleDateString();
        
        container.innerHTML = `
            <div class="profile-header fade-in">
                <div class="profile-avatar">
                    ${profile.username.charAt(0).toUpperCase()}
                </div>
                <h2>${profile.username}</h2>
                <p>${profile.email}</p>
                <div class="profile-keyword">
                    Your vibe: ${profile.keyword}
                </div>
                <p>Member since ${joinDate}</p>
            </div>
        `;
    }
    
    // Modal
    async openMemeModal(memeId) {
        try {
            // Find meme in current memes
            const meme = this.currentMemes.find(m => m.id === memeId);
            if (!meme) return;
            
            const modal = document.getElementById('modal');
            const modalBody = document.getElementById('modal-body');
            
            const uploadDate = new Date(meme.uploadDate).toLocaleDateString();
            
            modalBody.innerHTML = `
                <img src="${meme.imageUrl}" alt="${meme.title}">
                <h2>${meme.title}</h2>
                <p>By ${meme.uploadedBy} • ${uploadDate}</p>
                <p>Votes: ${meme.voteCount}</p>
                <div class="vote-buttons" style="margin-top: 1rem;">
                    <button class="btn btn-success ${meme.userVoteType === 'UPVOTE' ? 'voted' : ''}" 
                            onclick="memeApp.voteMeme(${meme.id}, 'UPVOTE')"
                            ${!this.currentUser ? 'disabled' : ''}>
                        👍 Upvote
                    </button>
                    <button class="btn btn-error ${meme.userVoteType === 'DOWNVOTE' ? 'voted' : ''}" 
                            onclick="memeApp.voteMeme(${meme.id}, 'DOWNVOTE')"
                            ${!this.currentUser ? 'disabled' : ''}>
                        👎 Downvote
                    </button>
                </div>
            `;
            
            modal.style.display = 'block';
        } catch (error) {
            console.error('Error opening modal:', error);
        }
    }
    
    closeModal() {
        document.getElementById('modal').style.display = 'none';
    }
    
    // Toast Notifications
    showToast(message, type = 'info') {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = message;
        
        container.appendChild(toast);
        
        // Auto remove after 5 seconds
        setTimeout(() => {
            if (toast.parentElement) {
                toast.parentElement.removeChild(toast);
            }
        }, 5000);
    }
}

// Global functions for onclick handlers
window.navigateToPage = function(page) {
    window.memeApp.navigateToPage(page);
};

// Initialize app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    window.memeApp = new MemeArenaApp();
});

// Make app globally accessible for debugging
window.MemeArenaApp = MemeArenaApp;
