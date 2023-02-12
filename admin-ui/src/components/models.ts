export class ShardInfo {
  index: { index: number, ofTotal: number, };

  messages: number;

  status: string;

  lastPicked: Date;

  constructor(
    index: { index: number, ofTotal: number, },
    messages: number,
    status: string,
    lastPicked: Date,
  ) {
    this.index = index;
    this.messages = messages;
    this.status = status;
    this.lastPicked = lastPicked;
  }
}

export interface ShardInfoList {
  shards: [ShardInfo]
}
