-- 1.参数列表
-- 1.1.排班id
local scheduleId = ARGV[1]
-- 1.2.患者id
local patientId = ARGV[2]
-- 1.3.挂号订单id
local appointmentId = ARGV[3]

-- 2.数据key
-- 2.1.号源库存key
local stockKey = 'schedule:stock:' .. scheduleId
-- 2.2.已挂号key
local bookedKey = 'schedule:booked:v2:' .. scheduleId

-- 3.脚本业务
-- 3.1.判断号源是否充足
local stock = tonumber(redis.call('get', stockKey))
if(stock == nil or stock <= 0) then
    -- 3.2.号源不足，返回1
    return 1
end
-- 3.2.判断患者是否已挂号
if(redis.call('hexists', bookedKey, patientId) == 1) then
    -- 3.3.存在，说明是重复挂号，返回2
    return 2
end
-- 3.4.扣减号源
redis.call('incrby', stockKey, -1)
-- 3.5.保存挂号患者
redis.call('hset', bookedKey, patientId, appointmentId)
-- 3.6.发送消息到队列中
redis.call('xadd', 'stream.appointments', '*', 'patientId', patientId, 'scheduleId', scheduleId, 'id', appointmentId)
return 0
