export function createShellElements(root = document) {
  const $ = (id) => (root.getElementById ? root.getElementById(id) : root.querySelector(`#${id}`))
  return {
    loginScreen: $('loginScreen'),
    productShell: $('productShell'),
    connectionStatus: $('connectionStatus'),
    apiBaseInput: $('apiBaseInput'),
    usernameInput: $('usernameInput'),
    passwordInput: $('passwordInput'),
    nicknameInput: $('nicknameInput'),
    toggleSidebarBtn: $('toggleSidebarBtn'),
    sidebarBackdrop: $('sidebarBackdrop'),
    contentTopbar: $('contentTopbar'),
    loginBtn: $('loginBtn'),
    registerBtn: $('registerBtn'),
    logoutBtn: $('logoutBtn'),
    buildVersion: $('buildVersion'),
    viewEyebrow: $('viewEyebrow'),
    viewTitle: $('viewTitle'),
    deleteConfirmModal: $('deleteConfirmModal'),
    deleteConfirmTitle: $('deleteConfirmTitle'),
    deleteConfirmMessage: $('deleteConfirmMessage'),
    deleteConfirmCloseBtn: $('deleteConfirmCloseBtn'),
    deleteConfirmCancelBtn: $('deleteConfirmCancelBtn'),
    deleteConfirmAcceptBtn: $('deleteConfirmAcceptBtn'),
    toast: $('toast'),
  }
}
