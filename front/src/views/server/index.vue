<template>
    <div class="container">
    <el-tabs class="tabs-white" v-model="activeTab" @tab-click="handleTabChange">
            <el-tab-pane label="资源监控" name="monitor">
                <div class="toolbar">
                    <el-select v-model="selectedServerId" placeholder="选择服务器" filterable clearable
                        @change="onServerChange" style="width:260px">
                        <el-option v-for="s in servers" :key="s.id" :label="s.name + ' (' + s.ip + ')'" :value="s.id" />
                    </el-select>
                    <el-button type="primary" @click="refreshMetrics" :loading="loadingMetrics"
                        style="margin-left:12px">刷新</el-button>
                    <el-switch v-model="autoRefresh" active-text="自动刷新" inactive-text="手动" @change="toggleAutoRefresh"
                        style="margin-left:16px" />
                </div>
                <el-card shadow="hover" class="cluster-card">
                    <div class="chart-title">集群汇总（平均/总量）</div>
                    <div class="cluster-summary">
                        <div class="summary-item">
                            <div class="label">在线服务器</div>
                            <div class="value">{{ clusterStats.online }} / {{ servers.length }}</div>
                        </div>
                        <div class="summary-item">
                            <div class="label">平均 CPU</div>
                            <div class="value">{{ clusterStats.cpuAvg }}%</div>
                        </div>
                        <div class="summary-item">
                            <div class="label">平均内存</div>
                            <div class="value">{{ clusterStats.memAvg }}%</div>
                        </div>
                        <div class="summary-item">
                            <div class="label">GPU 总数</div>
                            <div class="value">{{ clusterStats.gpuCount }}</div>
                            <div class="sub">均值 {{ clusterStats.gpuAvg }}%</div>
                        </div>
                        <div class="summary-item">
                            <div class="label">NPU 总数</div>
                            <div class="value">{{ clusterStats.npuCount }}</div>
                            <div class="sub">均值 {{ clusterStats.npuAvg }}%</div>
                        </div>
                    </div>
                    <div ref="clusterChartRef" class="chart-box" style="height:300px" />
                </el-card>
                <el-row :gutter="16" style="margin-top:16px">
                    <el-col :span="12"><el-card shadow="hover">
                            <div class="chart-title">CPU 使用率</div>
                            <div ref="cpuChartRef" class="chart-box" />
                        </el-card></el-col>
                    <el-col :span="12"><el-card shadow="hover">
                            <div class="chart-title">内存使用率</div>
                            <div ref="memChartRef" class="chart-box" />
                        </el-card></el-col>
                    <el-col :span="24" style="margin-top:16px"><el-card shadow="hover">
                            <div class="chart-title">GPU 利用率</div>
                            <div ref="gpuChartRef" class="chart-box" style="height:320px" />
                        </el-card></el-col>
                    <el-col :span="24" style="margin-top:16px"><el-card shadow="hover">
                            <div class="chart-title">NPU 利用率</div>
                            <div ref="npuChartRef" class="chart-box" style="height:320px" />
                        </el-card></el-col>
                </el-row>
                <el-alert type="info" show-icon style="margin-top:16px" :closable="false"
                    description="数据结构说明请查看本文件 <script> 部分顶部的注释（API 与字段定义）。" />
            </el-tab-pane>
            <el-tab-pane label="服务器列表" name="list">
                <div class="list-toolbar">
                    <el-button type="primary" icon="el-icon-plus" @click="openAddDialog">添加服务器</el-button>
                    <el-input v-model="search" placeholder="搜索名称/IP/标签" clearable style="width:260px; margin-left:12px"
                        @input="goPage(1)" />
                    <el-select v-model="pageSize" style="width:120px; margin-left:12px" @change="goPage(1)"><el-option
                            :value="10" label="每页 10 条" /><el-option :value="20" label="每页 20 条" /></el-select>
                </div>
                <el-row :gutter="16">
                    <el-col v-for="srv in pagedServers" :key="srv.id" :span="6">
                        <el-card :body-style="{ padding: '12px' }" class="server-card" shadow="hover">
                            <div class="server-card-header" @click="toggleExpand(srv.id)">
                                <div class="title"><span class="name">{{ srv.name }}</span><el-tag size="mini"
                                        :type="srv.status === 'online' ? 'success' : srv.status === 'offline' ? 'info' : 'warning'">{{
                                        statusLabel(srv.status) }}</el-tag></div>
                                <div class="sub"><i class="el-icon-connection"></i> {{ srv.ip }}</div>
                            </div>
                            <div class="summary">
                                <div class="kv"><span>CPU</span><b>{{ (srv.metrics && srv.metrics.cpu &&
                                        srv.metrics.cpu.usage) || 0 }}%</b></div>
                                <div class="kv"><span>内存</span><b>{{ (srv.metrics && srv.metrics.memory &&
                                        srv.metrics.memory.usagePct) || 0 }}%</b></div>
                                <div class="kv"><span>GPU数</span><b>{{ (srv.metrics && srv.metrics.gpu ?
                                        srv.metrics.gpu.length : 0) }}</b></div>
                                <div class="kv"><span>NPU数</span><b>{{ (srv.metrics && srv.metrics.npu ?
                                        srv.metrics.npu.length : 0) }}</b></div>
                            </div>
                            <div class="details" :class="{ expanded: expandedIds[srv.id] === true }">
                                <div class="detail-row"><span>操作系统：</span><b>{{ srv.os || '-' }}</b></div>
                                <div class="detail-row"><span>位置：</span><b>{{ srv.location || '-' }}</b></div>
                                <div class="detail-row"><span>标签：</span><el-tag v-for="t in srv.tags" :key="t"
                                        size="mini" style="margin-right:4px">{{ t }}</el-tag></div>
                                <div class="detail-row"><span>GPU：</span><span
                                        v-if="!(srv.metrics && srv.metrics.gpu && srv.metrics.gpu.length)">-</span>
                                    <ul v-else class="chip-list">
                                        <li v-for="g in srv.metrics.gpu" :key="g.index">#{{ g.index }} {{ g.name }} {{
                                            g.utilPct }}% {{ g.memUsedMB }}/{{ g.memTotalMB }}MB</li>
                                    </ul>
                                </div>
                                <div class="detail-row"><span>NPU：</span><span
                                        v-if="!(srv.metrics && srv.metrics.npu && srv.metrics.npu.length)">-</span>
                                    <ul v-else class="chip-list">
                                        <li v-for="n in srv.metrics.npu" :key="n.index">#{{ n.index }} {{ n.name }} {{
                                            n.utilPct }}% {{ n.temperatureC }}°C</li>
                                    </ul>
                                </div>
                            </div>
                        </el-card>
                    </el-col>
                </el-row>
                <div class="pager"><el-pagination background layout="total, prev, pager, next, jumper"
                        :total="filteredServers.length" :current-page="page" :page-size="pageSize"
                        @current-change="handlePageChange" /></div>
                <el-dialog title="添加服务器" :visible.sync="addVisible" width="520px">
                    <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
                        <el-form-item label="名称" prop="name"><el-input v-model="form.name"
                                maxlength="40" /></el-form-item>
                        <el-form-item label="IP" prop="ip"><el-input v-model="form.ip"
                                placeholder="例如 192.168.1.10" /></el-form-item>
                        <el-form-item label="位置"><el-input v-model="form.location" maxlength="80" /></el-form-item>
                        <el-form-item label="标签"><el-select v-model="form.tags" multiple filterable allow-create
                                default-first-option placeholder="输入后回车添加"><el-option v-for="t in allTags" :key="t"
                                    :label="t" :value="t" /></el-select></el-form-item>
                    </el-form>
                    <div slot="footer" class="dialog-footer"><el-button @click="addVisible = false">取
                            消</el-button><el-button type="primary" @click="submitAdd">保 存</el-button></div>
                </el-dialog>
            </el-tab-pane>
        </el-tabs>
    </div>
</template>

<script>
// 本页面仅实现前端展示逻辑，预留后端数据对接说明：
// 1) 获取服务器列表：GET /api/server/list?page={page}&size={size}&keyword={keyword?}
// 2) 获取实时监控指标：GET /api/server/metrics?serverId={id}&since={ts?}&points={n?}
// 3) 新增服务器：POST /api/server { name, ip, location?, tags? }
// 4) 单次快照：GET /api/server/snapshot?serverId={id}
import * as echarts from 'echarts'

export default {
    name: 'ServerIndex',
    data() {
        return {
            activeTab: 'monitor',
            // 列表
            servers: [],
            search: '',
            page: 1,
            pageSize: 10,
            expandedIds: {},
            addVisible: false,
            form: { name: '', ip: '', location: '', tags: [] },
            rules: {
                name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
                ip: [
                    { required: true, message: '请输入 IP', trigger: 'blur' },
                    { validator: (_, v, cb) => { const ok = /^(25[0-5]|2[0-4]\d|1?\d?\d)(\.(25[0-5]|2[0-4]\d|1?\d?\d)){3}$/.test(v); ok ? cb() : cb(new Error('IP 格式不正确')) }, trigger: 'blur' }
                ]
            },
            allTags: ['compute', 'training', 'edge', 'dev'],
            // 监控
            selectedServerId: null,
            loadingMetrics: false,
            autoRefresh: true,
            refreshTimer: null,
            // charts
            clusterChart: null, cpuChart: null, memChart: null, gpuChart: null, npuChart: null,
            // series
            timeline: [], seriesCPU: [], seriesMem: [], seriesGPU: [], seriesNPU: [],
            // 集群汇总
            clusterStats: { online: 0, cpuAvg: 0, memAvg: 0, gpuCount: 0, gpuAvg: 0, npuCount: 0, npuAvg: 0 },
            seriesCPUCluster: [], seriesMemCluster: [], seriesGPUCluster: [], seriesNPUCluster: []
        }
    },
    computed: {
        filteredServers() {
            const kw = (this.search || '').trim().toLowerCase()
            if (!kw) return this.servers
            return this.servers.filter(s =>
                (s.name || '').toLowerCase().includes(kw) ||
                (s.ip || '').toLowerCase().includes(kw) ||
                (s.tags || []).some(t => (t || '').toLowerCase().includes(kw))
            )
        },
        pagedServers() {
            const start = (this.page - 1) * this.pageSize
            return this.filteredServers.slice(start, start + this.pageSize)
        }
    },
    created() { this.fetchServers() },
    mounted() {
        if (this.activeTab === 'monitor') { this.$nextTick(() => { this.initCharts(); this.refreshMetrics(); this.startAutoRefresh() }) }
        window.addEventListener('resize', this.handleResize)
    },
    beforeDestroy() { window.removeEventListener('resize', this.handleResize); this.clearTimer(); this.disposeCharts() },
    methods: {
        // 列表 & 分页
        fetchServers() {
            // TODO: axios.get('/api/server/list', { params: { page: 1, size: 100 }})
            const mock = Array.from({ length: 28 }).map((_, i) => {
                const id = 'srv-' + (i + 1)
                const gpuCount = Math.floor(Math.random() * 4)
                const npuCount = Math.floor(Math.random() * 2)
                return { id, name: `Server-${i + 1}`, ip: `192.168.1.${i + 10}`, location: ['机房A', '机房B', '边缘C'][i % 3], os: ['Ubuntu 22.04', 'CentOS 7', 'Debian 12'][i % 3], tags: i % 2 === 0 ? ['compute', 'training'] : ['edge'], status: ['online', 'offline', 'maintain'][i % 3], metrics: { cpu: { usage: +(20 + Math.random() * 60).toFixed(1) }, memory: { usagePct: +(30 + Math.random() * 50).toFixed(1) }, gpu: Array.from({ length: gpuCount }).map((_, g) => ({ index: g, name: 'NVIDIA T4', utilPct: +(10 + Math.random() * 80).toFixed(0), memUsedMB: 500 + Math.floor(Math.random() * 7000), memTotalMB: 8192 })), npu: Array.from({ length: npuCount }).map((_, n) => ({ index: n, name: 'ASCEND-310', utilPct: +(10 + Math.random() * 80).toFixed(0), temperatureC: 40 + Math.floor(Math.random() * 40) })) }, updatedAt: new Date().toISOString() }
            })
            this.servers = mock; if (mock.length) { this.selectedServerId = mock[0].id }
            this.resetSeries(); this.updateClusterStats()
        },
        goPage(p) { this.page = p },
        handlePageChange(p) { this.page = p; window.scrollTo(0, 0) },
        openAddDialog() { this.form = { name: '', ip: '', location: '', tags: [] }; this.addVisible = true },
        submitAdd() { this.$refs.formRef.validate(valid => { if (!valid) return; const id = 'srv-' + (this.servers.length + 1); this.servers.unshift({ id, name: this.form.name, ip: this.form.ip, location: this.form.location, os: 'Ubuntu 22.04', tags: this.form.tags || [], status: 'online', metrics: { cpu: { usage: 0 }, memory: { usagePct: 0 }, gpu: [], npu: [] }, updatedAt: new Date().toISOString() }); this.addVisible = false; this.$message.success('已添加到本地列表（示例）'); this.goPage(1) }) },
        toggleExpand(id) { const next = !this.expandedIds[id]; this.$set(this.expandedIds, id, next); this.$nextTick(this.handleResize) },
        statusLabel(s) { return s === 'online' ? '在线' : s === 'offline' ? '离线' : '维护中' },

        // 资源监控
        handleTabChange(tab) { if (tab.name === 'monitor') { this.$nextTick(() => { this.initCharts(); this.refreshMetrics(); this.startAutoRefresh() }) } else { this.clearTimer() } },
        onServerChange() { this.resetSeries(); this.refreshMetrics() },
        initCharts() { if (this.$refs.clusterChartRef && !this.clusterChart) this.clusterChart = echarts.init(this.$refs.clusterChartRef); if (!this.cpuChart) this.cpuChart = echarts.init(this.$refs.cpuChartRef); if (!this.memChart) this.memChart = echarts.init(this.$refs.memChartRef); if (!this.gpuChart) this.gpuChart = echarts.init(this.$refs.gpuChartRef); if (!this.npuChart) this.npuChart = echarts.init(this.$refs.npuChartRef); this.renderAll() },
        disposeCharts() { ['clusterChart', 'cpuChart', 'memChart', 'gpuChart', 'npuChart'].forEach(k => { if (this[k]) { this[k].dispose(); this[k] = null } }) },
        handleResize() { [this.clusterChart, this.cpuChart, this.memChart, this.gpuChart, this.npuChart].forEach(c => c && c.resize()) },
        clearTimer() { if (this.refreshTimer) { clearInterval(this.refreshTimer); this.refreshTimer = null } },
        startAutoRefresh() { this.clearTimer(); if (!this.autoRefresh) return; this.refreshTimer = setInterval(this.refreshMetrics, 3000) },
        toggleAutoRefresh() { this.startAutoRefresh() },
        resetSeries() { this.timeline = []; this.seriesCPU = []; this.seriesMem = []; const srv = this.servers.find(s => s.id === this.selectedServerId) || { metrics: { gpu: [], npu: [] } }; this.seriesGPU = (srv.metrics.gpu || []).map(g => ({ name: `GPU${g.index}`, data: [] })); this.seriesNPU = (srv.metrics.npu || []).map(n => ({ name: `NPU${n.index}`, data: [] })); this.seriesCPUCluster = []; this.seriesMemCluster = []; this.seriesGPUCluster = []; this.seriesNPUCluster = [] },
        refreshMetrics() {
            if (!this.selectedServerId) return; this.loadingMetrics = true
            setTimeout(() => {
                const now = new Date(); const label = now.toLocaleTimeString(); const srv = this.servers.find(s => s.id === this.selectedServerId) || { metrics: { gpu: [], npu: [] } }
                const cpu = +(20 + Math.random() * 70).toFixed(1); const mem = +(30 + Math.random() * 60).toFixed(1)
                const gpuVals = (srv.metrics.gpu || []).map(() => +(10 + Math.random() * 85).toFixed(0)); const npuVals = (srv.metrics.npu || []).map(() => +(10 + Math.random() * 85).toFixed(0))
                const maxPoints = 30
                this.timeline.push(label); if (this.timeline.length > maxPoints) this.timeline.shift()
                this.seriesCPU.push(cpu); if (this.seriesCPU.length > maxPoints) this.seriesCPU.shift()
                this.seriesMem.push(mem); if (this.seriesMem.length > maxPoints) this.seriesMem.shift()
                this.seriesGPU.forEach((s, i) => { s.data.push(gpuVals[i] || 0); if (s.data.length > maxPoints) s.data.shift() })
                this.seriesNPU.forEach((s, i) => { s.data.push(npuVals[i] || 0); if (s.data.length > maxPoints) s.data.shift() })
                const cluster = this.computeClusterSnapshot(); this.seriesCPUCluster.push(cluster.cpuAvg); if (this.seriesCPUCluster.length > maxPoints) this.seriesCPUCluster.shift(); this.seriesMemCluster.push(cluster.memAvg); if (this.seriesMemCluster.length > maxPoints) this.seriesMemCluster.shift(); this.seriesGPUCluster.push(cluster.gpuAvg); if (this.seriesGPUCluster.length > maxPoints) this.seriesGPUCluster.shift(); this.seriesNPUCluster.push(cluster.npuAvg); if (this.seriesNPUCluster.length > maxPoints) this.seriesNPUCluster.shift(); this.clusterStats = cluster
                this.renderAll(); this.loadingMetrics = false
            }, 300)
        },
        renderAll() { this.renderCluster(); this.renderCPU(); this.renderMem(); this.renderGPU(); this.renderNPU() },
        renderCluster() { if (!this.clusterChart) return; this.clusterChart.setOption({ tooltip: { trigger: 'axis' }, legend: { data: ['CPU(均值)', '内存(均值)', 'GPU(均值)', 'NPU(均值)'] }, grid: { left: 40, right: 10, top: 30, bottom: 40 }, xAxis: { type: 'category', data: this.timeline }, yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } }, series: [{ type: 'line', name: 'CPU(均值)', data: this.seriesCPUCluster, smooth: true, symbol: 'none' }, { type: 'line', name: '内存(均值)', data: this.seriesMemCluster, smooth: true, symbol: 'none' }, { type: 'line', name: 'GPU(均值)', data: this.seriesGPUCluster, smooth: true, symbol: 'none' }, { type: 'line', name: 'NPU(均值)', data: this.seriesNPUCluster, smooth: true, symbol: 'none' }], color: ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C'] }) },
        renderCPU() { if (!this.cpuChart) return; this.cpuChart.setOption({ grid: { left: 40, right: 10, top: 30, bottom: 30 }, tooltip: { trigger: 'axis' }, xAxis: { type: 'category', boundaryGap: false, data: this.timeline }, yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } }, series: [{ type: 'line', name: 'CPU', data: this.seriesCPU, smooth: true, areaStyle: { opacity: 0.2 }, symbol: 'none' }], color: ['#409EFF'] }) },
        renderMem() { if (!this.memChart) return; const last = this.seriesMem[this.seriesMem.length - 1] || 0; this.memChart.setOption({ series: [{ type: 'gauge', min: 0, max: 100, axisLine: { lineStyle: { width: 12 } }, progress: { show: true, width: 12 }, detail: { valueAnimation: true, formatter: '{value}%', fontSize: 18 }, data: [{ value: last, name: '内存占用' }] }] }) },
        renderGPU() { if (!this.gpuChart) return; this.gpuChart.setOption({ tooltip: { trigger: 'axis' }, legend: { data: this.seriesGPU.map(s => s.name) }, grid: { left: 40, right: 10, top: 30, bottom: 40 }, xAxis: { type: 'category', data: this.timeline }, yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } }, series: this.seriesGPU.map(s => ({ type: 'line', name: s.name, data: s.data, smooth: true, symbol: 'none' })), color: ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C'] }) },
        renderNPU() { if (!this.npuChart) return; this.npuChart.setOption({ tooltip: { trigger: 'axis' }, legend: { data: this.seriesNPU.map(s => s.name) }, grid: { left: 40, right: 10, top: 30, bottom: 40 }, xAxis: { type: 'category', data: this.timeline }, yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } }, series: this.seriesNPU.map(s => ({ type: 'line', name: s.name, data: s.data, smooth: true, symbol: 'none' })), color: ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C'] }) },
        // 集群统计
        computeClusterSnapshot() {
            let online = 0, cpuSum = 0, memSum = 0, gpuCount = 0, gpuAvgSum = 0, npuCount = 0, npuAvgSum = 0
            const svrs = this.servers || []
            svrs.forEach(s => {
                if (s.status === 'online') online++
                const cpu = (s.metrics && s.metrics.cpu && s.metrics.cpu.usage) || 0
                const mem = (s.metrics && s.metrics.memory && s.metrics.memory.usagePct) || 0
                const j1 = cpu + (Math.random() * 2 - 1)
                const j2 = mem + (Math.random() * 2 - 1)
                cpuSum += Math.max(0, Math.min(100, j1))
                memSum += Math.max(0, Math.min(100, j2))
                const gpus = (s.metrics && s.metrics.gpu) || []
                const npus = (s.metrics && s.metrics.npu) || []
                gpuCount += gpus.length; npuCount += npus.length
                if (gpus.length) { const avg = gpus.reduce((a, b) => a + (b.utilPct || 0), 0) / gpus.length; gpuAvgSum += avg }
                if (npus.length) { const avg = npus.reduce((a, b) => a + (b.utilPct || 0), 0) / npus.length; npuAvgSum += avg }
            })
            const n = svrs.length || 1
            const cpuAvg = +(cpuSum / n).toFixed(1)
            const memAvg = +(memSum / n).toFixed(1)
            const gpuAvg = +(gpuAvgSum / (svrs.filter(s => (s.metrics && s.metrics.gpu && s.metrics.gpu.length)).length || 1)).toFixed(1)
            const npuAvg = +(npuAvgSum / (svrs.filter(s => (s.metrics && s.metrics.npu && s.metrics.npu.length)).length || 1)).toFixed(1)
            return { online, cpuAvg, memAvg, gpuCount, gpuAvg, npuCount, npuAvg }
        },
        updateClusterStats() { this.clusterStats = this.computeClusterSnapshot() }
    }
}
</script>

<style scoped>
.toolbar,
.list-toolbar {
    display: flex;
    align-items: center;
    margin-bottom: 12px;
}

.chart-box {
    width: 100%;
    height: 260px;
}

.chart-title {
    font-weight: 600;
    margin-bottom: 8px;
}

.cluster-card {
    margin-bottom: 16px;
}

.cluster-summary {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 12px;
    margin-bottom: 12px;
}

.cluster-summary .summary-item {
    background: #f7f8fa;
    border-radius: 6px;
    padding: 8px 10px;
}

.cluster-summary .summary-item .label {
    color: #909399;
    font-size: 12px;
}

.cluster-summary .summary-item .value {
    font-size: 16px;
    font-weight: 600;
    margin-top: 4px;
}

.cluster-summary .summary-item .sub {
    color: #909399;
    font-size: 12px;
    margin-top: 2px;
}

.server-card {
    margin-bottom: 16px;
    cursor: default;
}

.server-card .server-card-header .title {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.server-card .server-card-header .name {
    font-weight: 600;
    font-size: 15px;
}

.server-card .server-card-header .sub {
    color: #909399;
    margin-top: 4px;
}

.server-card .summary {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 6px;
    margin-top: 8px;
}

.server-card .summary .kv {
    background: #f7f8fa;
    border-radius: 4px;
    padding: 6px;
    text-align: center;
    font-size: 12px;
}

.server-card .summary .kv b {
    display: block;
    font-size: 14px;
    margin-top: 2px;
}

.server-card .details {
    margin-top: 8px;
    border-top: 1px dashed #e4e7ed;
    padding-top: 8px;
    height: 0;
    overflow: hidden;
    transition: height .2s ease;
}

.server-card .details.expanded {
    height: 160px;
}

.server-card .chip-list {
    list-style: none;
    padding: 0;
    margin: 0;
}

.server-card .chip-list li {
    font-size: 12px;
    background: #f5f7fa;
    display: inline-block;
    padding: 2px 6px;
    border-radius: 4px;
    margin-right: 6px;
    margin-bottom: 4px;
}

.pager {
    margin-top: 12px;
    text-align: right;
}
</style>

<style scoped>
/* 让 el-tabs 的整体背景也为白色，与页面其他元素统一 */
.tabs-white {
    background: #fff;
    border-radius: 6px;
    padding: 0 12px 12px;
}

/* 深度选择器，覆盖 tabs 的头部与内容区背景（兼容不同 vue-loader 语法） */
.tabs-white ::v-deep(.el-tabs__header),
.tabs-white /deep/ .el-tabs__header {
    background: #fff;
    margin: 0; /* 去掉默认下边距，让圆角更连贯 */
}

.tabs-white ::v-deep(.el-tabs__content),
.tabs-white /deep/ .el-tabs__content {
    background: #fff;
    padding-top: 12px;
}
</style>