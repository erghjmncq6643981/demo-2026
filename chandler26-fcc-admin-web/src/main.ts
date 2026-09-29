import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import App from './App.vue';
import './style.css';
import FccPagination from './components/FccPagination.vue';
import FccDateRangePicker from './components/FccDateRangePicker.vue';

const app = createApp(App);
app.use(createPinia());
app.use(ElementPlus, { locale: zhCn });
app.component('FccPagination', FccPagination);
app.component('FccDateRangePicker', FccDateRangePicker);
app.mount('#app');
