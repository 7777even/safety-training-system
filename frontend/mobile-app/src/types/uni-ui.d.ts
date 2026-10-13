/** 补齐 uni-ui 未经 package exports 暴露的 Vue 组件声明，供页面显式注册使用。 */
declare module '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue' {
  import type { DefineComponent } from 'vue';

  const UniIcons: DefineComponent;
  export default UniIcons;
}
