/** 图表数据来自已确认统计契约。 */
export interface ChartDatum { label: string; value: number }
/** 饼图只画非负事实，零总量不产生虚构扇区。 */
export function chartSegments(data: ChartDatum[]): { label: string; value: number; percent: number; offset: number }[] {
  const total=data.reduce((sum,item)=>sum+Math.max(0,item.value),0); let offset=0;
  return data.map(item=>{ const percent=total ? Math.max(0,item.value)*100/total : 0; const row={...item,percent,offset}; offset+=percent; return row; });
}
/** Canvas 与 SVG 共用已加载的数据，图片包含当前筛选说明及单位。 */
export async function statisticsPng(title: string, subtitle: string, unit: string, data: ChartDatum[], kind: 'bar' | 'pie'): Promise<Blob> {
  const canvas=document.createElement('canvas'); canvas.width=1000; canvas.height=Math.max(420,150+data.length*40);
  const ctx=canvas.getContext('2d'); if(!ctx) throw new Error('当前浏览器不支持统计图下载');
  ctx.fillStyle='#ffffff';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.fillStyle='#0f172a';ctx.font='bold 24px Microsoft YaHei';ctx.fillText(title,32,42);
  ctx.fillStyle='#64748b';ctx.font='14px Microsoft YaHei';ctx.fillText(subtitle,32,74);ctx.fillText('单位：'+unit,32,100);
  const colors=['#2563eb','#16a34a','#d97706','#7c3aed','#0891b2'];
  const max=Math.max(1,...data.map(item=>item.value));
  if(kind==='pie') {
    let start=-Math.PI/2;
    chartSegments(data).forEach((item,index)=>{
      const end=start+item.percent*Math.PI/50;ctx.beginPath();ctx.moveTo(230,260);ctx.arc(230,260,120,start,end);ctx.closePath();ctx.fillStyle=colors[index%colors.length] ?? '#2563eb';ctx.fill();start=end;
    });
  }
  data.forEach((item,index)=>{
    const y=145+index*40;
    if(kind==='pie') {ctx.fillStyle=colors[index%colors.length] ?? '#2563eb';ctx.fillRect(420,y-14,14,14);}
    ctx.fillStyle='#374151';ctx.fillText(item.label,kind==='pie'?444:32,y);
    if(kind==='bar') {ctx.fillStyle='#2563eb';ctx.fillRect(300,y-18,Math.max(0,item.value)/max*500,24);}
    ctx.fillStyle='#0f172a';ctx.fillText(String(item.value)+' '+unit,kind==='pie'?800:820,y);
  });
  if(!data.length)ctx.fillText('暂无统计数据',32,150);
  return new Promise((resolve,reject)=>canvas.toBlob(blob=>blob?resolve(blob):reject(new Error('统计图生成失败')),'image/png'));
}
