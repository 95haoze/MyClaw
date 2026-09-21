import {requestJson} from './http'

export interface WorkspaceDirectory {
    name: string;
    path: string
}

export interface WorkspaceDirectoryView {
    current: string;
    parent: string | null;
    directories: WorkspaceDirectory[];
    canBrowseRoots: boolean
}

export function browseWorkspaces(path = '.'): Promise<WorkspaceDirectoryView> {
    return requestJson(`/api/workspaces?path=${encodeURIComponent(path)}`)
}

export function selectWorkspaceDirectory(): Promise<string | null> {
    return requestJson('/api/workspaces/select-directory', {method: 'POST'})
}
