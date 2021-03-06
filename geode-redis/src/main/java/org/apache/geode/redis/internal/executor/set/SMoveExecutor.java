/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.redis.internal.executor.set;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.TimeoutException;
import org.apache.geode.redis.internal.AutoCloseableLock;
import org.apache.geode.redis.internal.ByteArrayWrapper;
import org.apache.geode.redis.internal.Command;
import org.apache.geode.redis.internal.ExecutionHandlerContext;
import org.apache.geode.redis.internal.RedisData;
import org.apache.geode.redis.internal.RedisDataType;
import org.apache.geode.redis.internal.RedisResponse;

public class SMoveExecutor extends SetExecutor {

  private static final int MOVED = 1;

  private static final int NOT_MOVED = 0;

  @Override
  public RedisResponse executeCommandWithResponse(Command command,
      ExecutionHandlerContext context) {
    List<byte[]> commandElems = command.getProcessedCommand();

    ByteArrayWrapper source = command.getKey();
    ByteArrayWrapper destination = new ByteArrayWrapper(commandElems.get(2));
    ByteArrayWrapper member = new ByteArrayWrapper(commandElems.get(3));

    checkDataType(source, RedisDataType.REDIS_SET, context);
    checkDataType(destination, RedisDataType.REDIS_SET, context);

    Region<ByteArrayWrapper, RedisData> region = getRegion(context);

    RedisResponse response;
    try (AutoCloseableLock regionLock = withRegionLock(context, source)) {
      RedisData sourceSet = region.get(source);

      if (sourceSet == null) {
        return RedisResponse.integer(NOT_MOVED);
      }

      boolean removed =
          new RedisSetInRegion(region).srem(source,
              new ArrayList<>(Collections.singletonList(member))) == 1;

      if (!removed) {
        return RedisResponse.integer(NOT_MOVED);
      }

      try (AutoCloseableLock destinationLock = withRegionLock(context, destination)) {
        // TODO: this should invoke a function in case the primary for destination is remote
        new RedisSetInRegion(region).sadd(destination,
            new ArrayList<>(Collections.singletonList(member)));

        response = RedisResponse.integer(MOVED);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        response = RedisResponse.error("Thread interrupted");
      } catch (TimeoutException e) {
        response = RedisResponse.error("Timeout acquiring lock. Please try again.");
      } catch (Exception e) {
        response = RedisResponse.error("Unexpected exception.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      response = RedisResponse.error("Thread interrupted.");
    } catch (TimeoutException e) {
      response = RedisResponse.error("Timeout acquiring lock. Please try again.");
    } catch (Exception e) {
      response = RedisResponse.error("Unexpected exception.");
    }

    return response;
  }
}
