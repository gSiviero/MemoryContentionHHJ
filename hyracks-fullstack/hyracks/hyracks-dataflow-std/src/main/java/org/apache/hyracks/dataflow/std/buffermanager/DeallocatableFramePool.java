/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hyracks.dataflow.std.buffermanager;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.hyracks.api.context.IHyracksFrameMgrContext;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DeallocatableFramePool implements IDeallocatableFramePool {
    private static final Logger LOGGER = LogManager.getLogger();
    private final IHyracksFrameMgrContext ctx;
    /**
     * Total size of Memory Budget in <b>FRAMES</b>
     */
    private int memBudget;
    /**
     * Total number of already allocated <b>FRAMES</b>
     */
    private int allocated;
    private LinkedList<ByteBuffer> buffers;

    private boolean isDynamic = false;
    private int desiredMemBudget;

    public DeallocatableFramePool(IHyracksFrameMgrContext ctx, int memBudgetInBytes) {
        this.ctx = ctx;
        this.memBudget = memBudgetInBytes;
        this.allocated = 0;
        this.buffers = new LinkedList<>();
        desiredMemBudget = memBudget;
    }

    public DeallocatableFramePool(IHyracksFrameMgrContext ctx, int memBudgetInBytes, boolean dynamic) {
        this(ctx, memBudgetInBytes);
        this.isDynamic = dynamic;
    }

    @Override
    public int getMinFrameSize() {
        return ctx.getInitialFrameSize();
    }

    @Override
    public int getMemoryBudgetBytes() {
        return memBudget;
    }

    @Override
    public ByteBuffer allocateFrame(int frameSize) throws HyracksDataException {
        ByteBuffer buffer = findExistingFrame(frameSize);
        if (buffer != null) {
            return buffer;
        }
        if (haveEnoughFreeSpace(frameSize)) {
            return createNewFrame(frameSize);
        }
        return mergeExistingFrames(frameSize);
    }

    private ByteBuffer mergeExistingFrames(int frameSize) throws HyracksDataException {
        int mergedSize = memBudget - allocated;
        for (Iterator<ByteBuffer> iter = buffers.iterator(); iter.hasNext();) {
            ByteBuffer buffer = iter.next();
            iter.remove();
            mergedSize += buffer.capacity();
            ctx.deallocateFrames(buffer.capacity());
            allocated -= buffer.capacity();
            if (mergedSize >= frameSize) {
                return createNewFrame(mergedSize);
            }
        }
        return null;

    }

    private ByteBuffer createNewFrame(int frameSize) throws HyracksDataException {
        allocated += frameSize;
        return ctx.allocateFrame(frameSize);
    }

    private boolean haveEnoughFreeSpace(int frameSize) {
        return allocated + frameSize <= memBudget;
    }

    private ByteBuffer findExistingFrame(int frameSize) {
        for (Iterator<ByteBuffer> iter = buffers.iterator(); iter.hasNext();) {
            ByteBuffer next = iter.next();
            if (next.capacity() >= frameSize) {
                iter.remove();
                return next;
            }
        }
        return null;
    }

    @Override
    public void deAllocateBuffer(ByteBuffer buffer) {
        if (shouldDeallocate(buffer)) {
            //Deallocate if Object is Large or it is a dynamic memory situation
            ctx.deallocateFrames(buffer.capacity());
            allocated -= buffer.capacity();
        } else {
            buffers.add(buffer);
        }
    }

    @Override
    public int getMemoryBudget() {
        return memBudget;
    }

    /**
     * Update Memory Budget;
     *
     * @param desiredSize Desired Size measured in frames
     */
    @Override
    public boolean updateMemoryBudget(int desiredSize) {
        desiredMemBudget = isDynamic ? desiredSize * getMinFrameSize() : memBudget;
        if (desiredMemBudget >= memBudget || allocated <= desiredMemBudget)
            memBudget = desiredMemBudget;
        return memBudget == desiredMemBudget;
    }

    private boolean shouldDeallocate(ByteBuffer buffer) {
        //Big Sized Object frame should always be deallocated.
        boolean shouldDeallocate = buffer.capacity() != ctx.getInitialFrameSize();
        // Memory is Dinamic and the number of allocated buffers is greater than the desiredMemoryBudget.
        shouldDeallocate = shouldDeallocate || (isDynamic && allocated > desiredMemBudget);
        return shouldDeallocate;
    }

    @Override
    public void reset() {
        allocated = 0;
        buffers.clear();
    }

    @Override
    public void close() {
        for (Iterator<ByteBuffer> iter = buffers.iterator(); iter.hasNext();) {
            ByteBuffer next = iter.next();
            ctx.deallocateFrames(next.capacity());
            iter.remove();
        }
        allocated = 0;
        buffers.clear();
    }
}
