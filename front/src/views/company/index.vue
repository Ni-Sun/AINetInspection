<template>
    <div class="container">
        <div class="team-info">
            <img class="logo" src="@/assets/images/logo.png" alt="logo" />
            <h2>西南交通大学</h2>
            <div class="contact">
            <div>邮箱：team@swjtu.edu.cn</div>
            <div>电话：028-87654321</div>
            <div>地址：四川省成都市金牛区二环路北一段111号</div>
            </div>
        </div>
    </div>
</template>

<script>
import { getDeviceStatus, getStatistics, getTodayAlarmTrend, getLastWeekAlarmTrend, getLastMonthAlarmTrend, getMonitorDevice, getLatestWarning, getWarningEventDetail } from "@/api/billboards";
import baseURL from "@/utils/request";
import livePlayer from "@/components/livePlayer.vue";
export default {
    components: { livePlayer },
    props: {},
    data() {
        return {
            loading: false,
            
        }
    },
    created() {
        this.initLoading();
    },
    mounted() {

    },
    beforeDestroy() {
        //清除定时器
        clearInterval(this.timer);
    },
    watch: {

    },
    computed: {},
    methods: {
        initLoading() {
            this.loading = true;
            
        },
        
        getLastMonthAlarmTrend() {
            this.chartLoading = true;
            getLastMonthAlarmTrend().then(res => {
                setTimeout(() => {
                    this.chartLoading = false;
                    if (res.code == 200) {
                        var result = res.data;
                        if (Object.keys(result).length > 0) {
                            var dataSets = [];
                            var categories = [];
                            var isfirst = true;
                            for (const key in result) {
                                var modelObject = {};
                                modelObject.name = key;
                                var dataArray = [];
                                for (const sonkey in result[key]) {
                                    if (isfirst) {
                                        categories.push(sonkey);
                                    }
                                    dataArray.push(result[key][sonkey]);
                                }
                                isfirst = false;
                                modelObject.data = dataArray;
                                dataSets.push(modelObject);
                            }
                            this.splineAreaChart.series = dataSets;
                            this.splineAreaChart.chartOptions.xaxis.categories = categories;
                        } else {
                            this.splineAreaChart.series = [];
                            this.splineAreaChart.chartOptions.xaxis.categories = [];
                        }
                    }
                }, 500);

            }).catch(() => {
                this.chartLoading = false;
            })
        },
        toMoreWarning() {
            this.$router.push("/warning");
        },
        viewDetail(row) {
            this.alarmLoading = true;
            getWarningEventDetail({ alertId: row.alertId }).then(res => {
                if (res.code == 200) {
                    this.dialogVisible = true;
                    this.alarmInfo = res.data;
                    if (Object.keys(this.alarmInfo).length > 0) {
                        this.alarmInfo.capturedImage = baseURL.split("/api")[0] + this.alarmInfo.capturedImage;
                        this.alarmInfo.capturedVideo = baseURL.split("/api")[0] + this.alarmInfo.capturedVideo;
                    }
                }
            }).finally(() => {
                this.alarmLoading = false;
            })

        },
        createDevice() {
            this.$router.push("/access");
        },
        createTask() {
            this.$router.push("/task");
        }
    }
};
</script>
<style lang="scss" scoped>

.team-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 60px;
}
.logo {
  width: 120px;
  height: 120px;
  margin-bottom: 20px;
}
h2 {
  margin: 0 0 16px 0;
  font-size: 28px;
  font-weight: bold;
  color: #222;
}
.contact > div {
  margin: 4px 0;
  color: #555;
  font-size: 16px;
}
canvas {
    background-color: #000;
    object-fit: fill;
}

.stats-label {
    font-size: 14px;
}

.stats-value {
    color: #343a40;
    font-family: Inter, sans-serif;
    font-size: 22px;
    margin-top: 8px;
}


.stats-digit {
    display: flex;
    align-items: flex-start;
    border-bottom-left-radius: 0;
    border-bottom-right-radius: 0;

    .card-body {
        flex: 1;
    }

    .card-icon i {
        font-size: 28px;
    }
}

.stats-trend {
    border-top-left-radius: 0;
    border-top-right-radius: 0;

    .card-body {
        display: flex;
        align-items: center;

        .stats-value-ratio {
            font-size: 11px;
            margin-right: 8px;

            i {
                margin-right: 3px;
            }

            .icon-yuandian {
                font-size: 11px;
            }
        }

        .stats-value-desc {
            font-size: 14px;
        }
    }
}

.layout-top {
    height: 32px;
    margin-bottom: 10px;

    .title {
        color: #343a40;
        font-size: 15px;
        font-weight: 500;
        font-family: Inter, sans-serif;
        // display: flex;
        // align-items: center;

        i {
            margin-right: 5px;
            font-size: 18px;
        }
    }

    .action {
        padding: 0 6px;

        .chart-filter {

            &.active {
                color: #FFFFFF;
                background-color: #5664d2;
                border-color: #5664d2;
            }
        }
    }
}

.layout-content {

    .realtime-video {
        position: relative;
    }

    .chart-empty,
    .footage-empty,
    .alarm-empty {
        display: flex;
        justify-content: center;
        align-items: center;
    }

    .simple-list {
        height: 45vh;
        padding-right: 15px;
        overflow: hidden;

        &:after {
            content: " ";
            display: table;
        }

        &:before {
            content: " ";
            display: table;
        }

        &::-webkit-scrollbar {
            width: 0;
        }
    }


    .list-unstyled {
        padding-left: 0;
        list-style: none;
        animation-timing-function: linear;
        margin-top: 8px;
        margin-left: 16px;
    }

    .activity-wid {

        .activity-list {

            &:before {
                content: "";
                border-left: 2px dashed rgba(86, 100, 210, .25);
                position: absolute;
                left: 0;
                bottom: 0;
                top: 32px;
            }
        }
    }

    .activity-list {
        position: relative;
        padding: 0 0 40px 30px;

        // &:last-child {
        //     padding-bottom: 0;
        // }

        .activity-icon {
            position: absolute;
            left: -15px;
            top: 0;
            z-index: 9;

            .avatar-title {
                align-items: center;
                display: flex;
                font-weight: 500;
                height: 100%;
                justify-content: center;
                width: 100%;
            }
        }

        .avatar-xs {
            width: 32px;
            height: 32px;
        }

        .event-list {

            .event-list-item {
                display: flex;
                justify-content: space-between;
                align-items: center;

                .event-list-item-left {
                    width: 65%;

                    .event-date {
                        color: #030a1a;
                        font-family: Inter, sans-serif;
                        font-weight: 500;
                        margin-bottom: 8px;

                        small {
                            font-size: 80%;
                            font-weight: 400;
                        }
                    }

                    .event-name {
                        font-size: 14px;
                        line-height: 24px;

                        .value {
                            color: #030a1a;
                        }
                    }
                }

                .event-list-item-right {
                    width: 30%;

                    .event-image {
                        height: 70px;

                        img {
                            width: 100%;
                            height: 100%;
                            object-fit: cover;
                            border-radius: 4px;
                        }
                    }
                }
            }
        }
    }
}

.dialog-wrap {
    max-height: 55vh;
    // overflow: auto;
    padding: 0 10px;

    .detail {
        display: flex;
        justify-content: space-between;

        .left-box {
            width: 42%;
            padding: 12px 20px;
            box-sizing: border-box;

            .result-item {

                &:not(:last-child) {
                    margin-bottom: 24px;
                }


                .result-item-key {
                    color: #a6a6a6;
                }



                .result-item-value {

                    .image-wrap {
                        position: relative;

                        .image-empty {
                            width: 100%;
                            height: 220px;
                            background-color: #D9D9D9;
                            display: flex;
                            justify-content: center;
                            align-items: center;

                            i {
                                font-size: 24px;
                            }
                        }
                    }


                }
            }

        }

        .right-box {
            flex: 1;
            margin-left: 20px;
            box-sizing: border-box;

            .title {
                color: #1b1e26;
                font-size: 15px;
                margin-bottom: 6px;
            }

            .camera-wrap {
                width: 100%;
                height: calc(100% - 24px);
                background: #1e1e1e;

                video {
                    width: 100%;
                    height: 100%;
                }
            }

        }
    }
}
</style>