local bookedId = redis.call('hget', KEYS[2], ARGV[1])
if bookedId ~= ARGV[2] then
    return 0
end
if redis.call('exists', KEYS[3]) == 1 then
    return 0
end
redis.call('set', KEYS[3], '1', 'EX', 2592000)
redis.call('hdel', KEYS[2], ARGV[1])
redis.call('incrby', KEYS[1], 1)
return 1
